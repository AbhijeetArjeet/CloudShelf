package com.example.cloudshelf.sync

import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SyncClient(
    private val db: CloudShelfDb
) {
    companion object {
        const val CHUNK_SIZE = 2 * 1024 * 1024 // 2 MB chunks
    }

    /**
     * Connects and authenticates with Pixel receiver during pairing.
     */
    suspend fun pairWithReceiver(
        host: String,
        port: Int,
        pin: String,
        companionDeviceName: String
    ): PairedDevice = withContext(Dispatchers.IO) {
        val companionDeviceId = "companion_${android.os.Build.MODEL.replace(" ", "_")}"
        val url = URL("http://$host:$port/api/v1/pair")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }

        val requestPayload = JSONObject().apply {
            put("deviceId", companionDeviceId)
            put("deviceName", companionDeviceName)
            put("pairingPin", pin)
            put("port", port)
        }

        conn.outputStream.use { it.write(requestPayload.toString().toByteArray()) }

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Pairing rejected by Pixel: HTTP ${conn.responseCode} - $error")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val respJson = JSONObject(responseText)
        val receiverId = respJson.getString("receiverDeviceId")
        val receiverName = respJson.getString("receiverName")
        val authToken = respJson.getString("authToken")

        val paired = PairedDevice(
            id = receiverId,
            deviceName = receiverName,
            role = DeviceRole.RECEIVER_PIXEL,
            endpointHost = host,
            endpointPort = port,
            authToken = authToken,
            pairingStatus = PairingStatus.PAIRED
        )
        db.insertOrUpdateDevice(paired)
        paired
    }

    /**
     * Requirement 10 & 12: Backpressure check. Queries Pixel safe available staging room.
     */
    suspend fun queryPixelCapacity(device: PairedDevice): DeviceCapacityInfo = withContext(Dispatchers.IO) {
        val url = URL("http://${device.endpointHost}:${device.endpointPort}/api/v1/capacity")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${device.authToken}")
            connectTimeout = 8000
            readTimeout = 8000
        }

        if (conn.responseCode != 200) {
            throw IllegalStateException("Failed to query Pixel staging capacity: HTTP ${conn.responseCode}")
        }

        val respText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(respText)
        DeviceCapacityInfo(
            stagingLimitBytes = json.getLong("stagingLimitBytes"),
            stagedBytes = json.getLong("stagedBytes"),
            safeAvailableBytes = json.getLong("safeAvailableBytes"),
            stagingFolder = json.getString("stagingFolder"),
            isAcceptingTransfers = json.getBoolean("isAcceptingTransfers"),
            statusMessage = json.getString("statusMessage")
        )
    }

    /**
     * Probe endpoint to check how many bytes were already received (resumability).
     */
    suspend fun probeTransferOffset(device: PairedDevice, fileId: String): Long = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${device.endpointHost}:${device.endpointPort}/api/v1/transfer/probe?fileId=$fileId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(resp).optLong("bytesReceived", 0L)
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Streams file in 2 MB chunks with automatic offset resumption and SHA-256 verification.
     * CRITICAL INVARIANT: NEVER deletes the source file!
     */
    suspend fun transferFileResumable(
        device: PairedDevice,
        manifestItem: TransferManifestItem,
        fileStream: InputStream,
        expectedSha256: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val fileId = manifestItem.id
        val totalSize = manifestItem.sizeBytes

        // Step 1: Probe already transferred offset
        val existingOffset = probeTransferOffset(device, fileId)
        if (existingOffset > 0) {
            var skipped = 0L
            while (skipped < existingOffset) {
                val skipStep = fileStream.skip(existingOffset - skipped)
                if (skipStep <= 0) break
                skipped += skipStep
            }
        }

        var currentOffset = existingOffset
        val chunkBuffer = ByteArray(CHUNK_SIZE)

        // Step 2: Stream remaining chunks
        while (currentOffset < totalSize) {
            val bytesToRead = (totalSize - currentOffset).coerceAtMost(CHUNK_SIZE.toLong()).toInt()
            var bytesReadTotal = 0
            while (bytesReadTotal < bytesToRead) {
                val read = fileStream.read(chunkBuffer, bytesReadTotal, bytesToRead - bytesReadTotal)
                if (read == -1) break
                bytesReadTotal += read
            }
            if (bytesReadTotal == 0) break

            val chunkUrl = URL("http://${device.endpointHost}:${device.endpointPort}/api/v1/transfer/chunk")
            val conn = (chunkUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("X-File-Id", fileId)
                setRequestProperty("X-Chunk-Offset", currentOffset.toString())
                setRequestProperty("X-Total-Size", totalSize.toString())
                setRequestProperty("X-Filename", manifestItem.filename)
                setRequestProperty("X-Mime-Type", manifestItem.mimeType)
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
            }

            conn.outputStream.use { it.write(chunkBuffer, 0, bytesReadTotal) }
            if (conn.responseCode != 200) {
                return@withContext false
            }

            currentOffset += bytesReadTotal
            onProgress(currentOffset, totalSize)

            // Update local manifest
            db.insertOrUpdateManifestItem(manifestItem.copy(bytesTransferred = currentOffset, status = TransferStatus.TRANSFERRING))
        }

        // Step 3: Completion & SHA-256 verification handshake
        val completeUrl = URL("http://${device.endpointHost}:${device.endpointPort}/api/v1/transfer/complete")
        val completeConn = (completeUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-File-Id", fileId)
            setRequestProperty("X-Expected-Sha256", expectedSha256)
            setRequestProperty("X-Filename", manifestItem.filename)
            setRequestProperty("X-Mime-Type", manifestItem.mimeType)
            setRequestProperty("X-Device-Id", device.id)
            connectTimeout = 15000
            readTimeout = 30000
        }

        val success = completeConn.responseCode == 200
        if (success) {
            db.insertOrUpdateManifestItem(manifestItem.copy(bytesTransferred = totalSize, status = TransferStatus.PIXEL_STAGED))
        }
        success
    }
}
