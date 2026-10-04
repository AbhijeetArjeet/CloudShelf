package com.example.cloudshelf.providers.takeout

import com.example.cloudshelf.model.SourceType
import com.example.cloudshelf.providers.MediaSourceProvider
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.providers.SourceItemRef
import com.example.cloudshelf.providers.SourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class GoogleTakeoutProvider(
    private val archiveFiles: List<File>,
    private val stagingLimitBytes: Long = 3L * 1024 * 1024 * 1024
) : MediaSourceProvider {

    override val sourceType: SourceType = SourceType.TAKEOUT

    private val supportedImageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "avif", "dng", "raw"
    )
    private val supportedVideoExtensions = setOf(
        "mp4", "mov", "m4v", "3gp", "mkv", "webm"
    )

    override suspend fun scanSource(): ScanSummary = withContext(Dispatchers.IO) {
        val items = mutableListOf<SourceItemRef>()
        var photoCount = 0
        var videoCount = 0
        var totalBytes = 0L

        for (archiveFile in archiveFiles) {
            if (!archiveFile.exists() || !archiveFile.canRead()) continue

            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                // Map of sidecar JSON files by entry name
                val sidecarMap = mutableMapOf<String, ZipEntry>()
                entries.forEach { entry ->
                    if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                        sidecarMap[entry.name] = entry
                    }
                }

                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val isPhoto = supportedImageExtensions.contains(ext)
                    val isVideo = supportedVideoExtensions.contains(ext)

                    if (isPhoto || isVideo) {
                        val filename = entry.name.substringAfterLast('/')
                        val mimeType = if (isPhoto) "image/$ext" else "video/$ext"
                        val size = entry.size.coerceAtLeast(0L)

                        if (isPhoto) photoCount++ else videoCount++
                        totalBytes += size

                        // Check for matching sidecar JSON
                        val metadata = findAndParseSidecar(zip, entry.name, sidecarMap)

                        items.add(
                            SourceItemRef(
                                identifier = "${archiveFile.absolutePath}#${entry.name}",
                                filename = filename,
                                mimeType = mimeType,
                                estimatedSizeBytes = size,
                                sidecarMetadata = metadata
                            )
                        )
                    }
                }
            }
        }

        val estimatedBatches = if (totalBytes > 0) {
            ((totalBytes + stagingLimitBytes - 1) / stagingLimitBytes).toInt().coerceAtLeast(1)
        } else 0

        ScanSummary(
            sourceType = SourceType.TAKEOUT,
            sourceName = if (archiveFiles.size == 1) archiveFiles.first().name else "${archiveFiles.size} Takeout Archives",
            totalItems = items.size,
            photoCount = photoCount,
            videoCount = videoCount,
            totalSizeBytes = totalBytes,
            estimatedBatches = estimatedBatches,
            items = items
        )
    }

    override suspend fun openItemStream(itemRef: SourceItemRef): InputStream = withContext(Dispatchers.IO) {
        val parts = itemRef.identifier.split("#", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Invalid Takeout item identifier: ${itemRef.identifier}")
        val archiveFile = File(parts[0])
        val entryName = parts[1]

        val zip = ZipFile(archiveFile)
        val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("Entry not found in archive: $entryName")
        
        // Return stream wrapped to ensure zip closes when stream is closed
        val entryStream = zip.getInputStream(entry)
        object : InputStream() {
            override fun read(): Int = entryStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = entryStream.read(b, off, len)
            override fun close() {
                try {
                    entryStream.close()
                } finally {
                    zip.close()
                }
            }
        }
    }

    private fun findAndParseSidecar(
        zip: ZipFile,
        mediaEntryName: String,
        sidecars: Map<String, ZipEntry>
    ): SourceMetadata? {
        // Strategy 1: exact name + .json (e.g. IMG_1234.jpg.json)
        // Strategy 2: replace extension with .json (e.g. IMG_1234.json)
        val sidecarEntry = sidecars["$mediaEntryName.json"]
            ?: sidecars[mediaEntryName.substringBeforeLast('.') + ".json"]
            ?: return null

        return try {
            val jsonString = zip.getInputStream(sidecarEntry).bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val title = obj.optString("title").takeIf { it.isNotBlank() }
            val description = obj.optString("description").takeIf { it.isNotBlank() }
            val photoTakenTime = obj.optJSONObject("photoTakenTime")?.optLong("timestamp")
            val geoData = obj.optJSONObject("geoData")
            val lat = geoData?.optDouble("latitude")?.takeIf { !it.isNaN() && it != 0.0 }
            val lon = geoData?.optDouble("longitude")?.takeIf { !it.isNaN() && it != 0.0 }

            SourceMetadata(
                title = title,
                description = description,
                timestampMs = photoTakenTime?.let { it * 1000 },
                latitude = lat,
                longitude = lon
            )
        } catch (e: Exception) {
            null
        }
    }
}
