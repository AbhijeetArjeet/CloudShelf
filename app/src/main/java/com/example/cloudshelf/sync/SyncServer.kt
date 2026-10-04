package com.example.cloudshelf.sync

import android.content.Context
import android.os.Environment
import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.model.*
import com.example.cloudshelf.storage.MediaStoreBridge
import com.example.cloudshelf.storage.SafeFileIO
import com.example.cloudshelf.storage.StorageGuard
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.util.UUID

class SyncServer(
    private val context: Context,
    private val db: CloudShelfDb,
    private val storageGuard: StorageGuard,
    private val mediaStoreBridge: MediaStoreBridge
) {
    private var server: HttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile var activePairingPayload: PairingPayload? = null
        private set

    fun generatePairingCredentials(hostIp: String, port: Int = 8844): PairingPayload {
        val pin = String.format("%06d", (100000..999999).random())
        val token = UUID.randomUUID().toString()
        val payload = PairingPayload(
            deviceId = "pixel_${android.os.Build.MODEL.replace(" ", "_")}",
            deviceName = "Google Pixel (${android.os.Build.MODEL})",
            host = hostIp,
            port = port,
            pairingPin = pin,
            pairingToken = token
        )
        activePairingPayload = payload
        return payload
    }

    fun start(port: Int = 8844) {
        stop()
        val s = HttpServer.create(InetSocketAddress(port), 0)

        // 1. Pairing endpoint
        s.createContext("/api/v1/pair") { exchange ->
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return@createContext
            }
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val submittedPin = json.optString("pairingPin")
            val submittedToken = json.optString("pairingToken")
            val deviceId = json.optString("deviceId")
            val deviceName = json.optString("deviceName")
            val endpointHost = exchange.remoteAddress.address.hostAddress ?: ""
            val endpointPort = json.optInt("port", 8844)

            val currentPayload = activePairingPayload
            if (currentPayload != null && (submittedPin == currentPayload.pairingPin || submittedToken == currentPayload.pairingToken)) {
                val authToken = UUID.randomUUID().toString()
                scope.launch {
                    db.insertOrUpdateDevice(
                        PairedDevice(
                            id = deviceId,
                            deviceName = deviceName,
                            role = DeviceRole.SOURCE_COMPANION,
                            endpointHost = endpointHost,
                            endpointPort = endpointPort,
                            authToken = authToken,
                            pairingStatus = PairingStatus.PAIRED
                        )
                    )
                }
                activePairingPayload = null // Consume one-time pairing token
                val responseJson = JSONObject().apply {
                    put("receiverDeviceId", currentPayload.deviceId)
                    put("receiverName", currentPayload.deviceName)
                    put("authToken", authToken)
                }
                sendResponse(exchange, 200, responseJson.toString(), "application/json")
            } else {
                sendResponse(exchange, 401, "Invalid pairing PIN or token")
            }
        }

        // 2. Capacity & Backpressure endpoint
        s.createContext("/api/v1/capacity") { exchange ->
            if (exchange.requestMethod != "GET") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return@createContext
            }
            scope.launch {
                val activeBatch = db.getActiveBatch()
                val minReserve = 5L * 1024 * 1024 * 1024 // 5 GB
                val targetLimit = 3L * 1024 * 1024 * 1024 // 3 GB
                val storage = storageGuard.getStorageStatus(minReserve)

                val stagedBytes = activeBatch?.actualSizeBytes ?: 0L
                val isWaitingBackup = activeBatch != null && (activeBatch.state == BatchState.READY_FOR_BACKUP || activeBatch.state == BatchState.BACKUP_PENDING)

                val safeAvailable = if (isWaitingBackup) {
                    0L // Backpressure active! Do not accept more until backup confirmed
                } else {
                    (targetLimit - stagedBytes).coerceAtLeast(0L).coerceAtMost(storage.safeAvailableBytes)
                }

                val statusMessage = when {
                    isWaitingBackup -> "WAITING_FOR_GOOGLE_PHOTOS_BACKUP"
                    safeAvailable <= 0 -> "WAITING_FOR_PIXEL_STORAGE"
                    else -> "ACCEPTING_MEDIA"
                }

                val response = JSONObject().apply {
                    put("stagingLimitBytes", targetLimit)
                    put("stagedBytes", stagedBytes)
                    put("safeAvailableBytes", safeAvailable)
                    put("stagingFolder", activeBatch?.stagingFolder ?: "Pictures/CloudShelf/Staging/Batch-001")
                    put("isAcceptingTransfers", safeAvailable > 0 && !isWaitingBackup)
                    put("statusMessage", statusMessage)
                }
                sendResponse(exchange, 200, response.toString(), "application/json")
            }
        }

        // 3. Probe offset for resumable chunks
        s.createContext("/api/v1/transfer/probe") { exchange ->
            val query = exchange.requestURI.query ?: ""
            val fileId = query.substringAfter("fileId=").substringBefore("&")
            val tempFile = File(context.cacheDir, "transfers/$fileId.tmp")
            val bytesReceived = if (tempFile.exists()) tempFile.length() else 0L
            val resp = JSONObject().apply { put("bytesReceived", bytesReceived) }
            sendResponse(exchange, 200, resp.toString(), "application/json")
        }

        // 4. Chunk ingestion
        s.createContext("/api/v1/transfer/chunk") { exchange ->
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return@createContext
            }
            val fileId = exchange.requestHeaders.getFirst("X-File-Id") ?: UUID.randomUUID().toString()
            val offset = exchange.requestHeaders.getFirst("X-Chunk-Offset")?.toLongOrNull() ?: 0L

            val transfersDir = File(context.cacheDir, "transfers").apply { if (!exists()) mkdirs() }
            val tempFile = File(transfersDir, "$fileId.tmp")

            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(offset)
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (exchange.requestBody.read(buffer).also { read = it } != -1) {
                    raf.write(buffer, 0, read)
                }
            }

            val resp = JSONObject().apply { put("bytesReceived", tempFile.length()) }
            sendResponse(exchange, 200, resp.toString(), "application/json")
        }

        // 5. Completion & SHA-256 verification
        s.createContext("/api/v1/transfer/complete") { exchange ->
            if (exchange.requestMethod != "POST") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return@createContext
            }
            val fileId = exchange.requestHeaders.getFirst("X-File-Id") ?: ""
            val expectedHash = exchange.requestHeaders.getFirst("X-Expected-Sha256") ?: ""
            val filename = exchange.requestHeaders.getFirst("X-Filename") ?: "photo_$fileId.jpg"
            val mimeType = exchange.requestHeaders.getFirst("X-Mime-Type") ?: "image/jpeg"
            val deviceId = exchange.requestHeaders.getFirst("X-Device-Id") ?: "unknown"

            val tempFile = File(context.cacheDir, "transfers/$fileId.tmp")
            if (!tempFile.exists()) {
                sendResponse(exchange, 404, "Incomplete transfer file not found")
                return@createContext
            }

            scope.launch {
                val calculatedHash = StreamingHasher.computeSha256(tempFile)
                if (expectedHash.isNotBlank() && !calculatedHash.equals(expectedHash, ignoreCase = true)) {
                    tempFile.delete()
                    sendResponse(exchange, 400, "SHA-256 verification failed! Corrupted transfer rejected.")
                    return@launch
                }

                val activeBatch = db.getActiveBatch() ?: run {
                    val newBatchId = UUID.randomUUID().toString()
                    val stagingFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CloudShelf/Staging/Batch-001").apply { if (!exists()) mkdirs() }
                    val b = StagingBatch(
                        id = newBatchId,
                        jobId = UUID.randomUUID().toString(),
                        batchIndex = 1,
                        totalBatchesEstimated = 1,
                        state = BatchState.IMPORTING,
                        targetSizeBytes = 3L * 1024 * 1024 * 1024,
                        stagingFolder = stagingFolder.absolutePath
                    )
                    db.insertOrUpdateBatch(b)
                    b
                }

                val stagingDir = File(activeBatch.stagingFolder).apply { if (!exists()) mkdirs() }
                val targetFile = SafeFileIO.resolveFilenameCollision(stagingDir, filename)
                tempFile.renameTo(targetFile)

                val mediaStoreUri = mediaStoreBridge.publishToMediaStore(
                    sourceFile = targetFile,
                    mimeType = mimeType,
                    relativePath = "Pictures/CloudShelf/Staging/Batch-${String.format("%03d", activeBatch.batchIndex)}/",
                    displayName = targetFile.name
                )

                val mediaItem = MediaItem(
                    id = fileId,
                    jobId = activeBatch.jobId,
                    batchId = activeBatch.id,
                    sourceType = SourceType.LOCAL_FOLDER,
                    sourceIdentifier = "device://$deviceId/$filename",
                    originalFilename = filename,
                    normalizedFilename = targetFile.name,
                    stagingRelativePath = "Pictures/CloudShelf/Staging/Batch-${String.format("%03d", activeBatch.batchIndex)}/",
                    mimeType = mimeType,
                    sizeBytes = targetFile.length(),
                    sha256Hash = calculatedHash,
                    state = MediaState.LOCAL_VERIFIED,
                    localUri = targetFile.absolutePath,
                    mediaStoreUri = mediaStoreUri?.toString(),
                    verifiedAt = System.currentTimeMillis()
                )
                db.insertMediaItem(mediaItem)

                val resp = JSONObject().apply {
                    put("status", "VERIFIED_AND_STAGED")
                    put("mediaId", fileId)
                    put("filename", targetFile.name)
                    put("sha256", calculatedHash)
                }
                sendResponse(exchange, 200, resp.toString(), "application/json")
            }
        }

        s.executor = null
        s.start()
        server = s
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, text: String, contentType: String = "text/plain") {
        exchange.responseHeaders.set("Content-Type", contentType)
        val bytes = text.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
