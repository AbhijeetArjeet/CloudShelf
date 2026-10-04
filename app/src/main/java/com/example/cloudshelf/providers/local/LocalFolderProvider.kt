package com.example.cloudshelf.providers.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.cloudshelf.model.SourceType
import com.example.cloudshelf.providers.MediaSourceProvider
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.providers.SourceItemRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class LocalFolderProvider(
    private val context: Context,
    private val folderUri: Uri,
    private val folderName: String,
    private val stagingLimitBytes: Long = 3L * 1024 * 1024 * 1024
) : MediaSourceProvider {

    override val sourceType: SourceType = SourceType.LOCAL_FOLDER

    override suspend fun scanSource(): ScanSummary = withContext(Dispatchers.IO) {
        val items = mutableListOf<SourceItemRef>()
        var photoCount = 0
        var videoCount = 0
        var totalBytes = 0L

        if (folderUri.scheme == "file") {
            val dir = File(folderUri.path ?: "")
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        val isPhoto = ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif")
                        val isVideo = ext in setOf("mp4", "mov", "m4v", "3gp", "mkv")
                        if (isPhoto || isVideo) {
                            val size = file.length()
                            if (isPhoto) photoCount++ else videoCount++
                            totalBytes += size
                            items.add(
                                SourceItemRef(
                                    identifier = file.absolutePath,
                                    filename = file.name,
                                    mimeType = if (isPhoto) "image/$ext" else "video/$ext",
                                    estimatedSizeBytes = size
                                )
                            )
                        }
                    }
                }
            }
        } else {
            val docFile = DocumentFile.fromTreeUri(context, folderUri)
            if (docFile != null && docFile.isDirectory) {
                scanDocumentFileTree(docFile, items)
                items.forEach { item ->
                    if (item.mimeType.startsWith("image/")) photoCount++ else videoCount++
                    totalBytes += item.estimatedSizeBytes
                }
            }
        }

        val estimatedBatches = if (totalBytes > 0) {
            ((totalBytes + stagingLimitBytes - 1) / stagingLimitBytes).toInt().coerceAtLeast(1)
        } else 0

        ScanSummary(
            sourceType = SourceType.LOCAL_FOLDER,
            sourceName = folderName,
            totalItems = items.size,
            photoCount = photoCount,
            videoCount = videoCount,
            totalSizeBytes = totalBytes,
            estimatedBatches = estimatedBatches,
            items = items
        )
    }

    private fun scanDocumentFileTree(dir: DocumentFile, items: MutableList<SourceItemRef>) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                scanDocumentFileTree(file, items)
            } else if (file.isFile) {
                val mime = file.type ?: ""
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    items.add(
                        SourceItemRef(
                            identifier = file.uri.toString(),
                            filename = file.name ?: "media_item",
                            mimeType = mime,
                            estimatedSizeBytes = file.length()
                        )
                    )
                }
            }
        }
    }

    override suspend fun openItemStream(itemRef: SourceItemRef): InputStream = withContext(Dispatchers.IO) {
        if (itemRef.identifier.startsWith("content://")) {
            val uri = Uri.parse(itemRef.identifier)
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open input stream for: ${itemRef.identifier}")
        } else {
            FileInputStream(File(itemRef.identifier))
        }
    }
}
