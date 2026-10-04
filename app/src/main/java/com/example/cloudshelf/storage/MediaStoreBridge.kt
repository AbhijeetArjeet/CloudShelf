package com.example.cloudshelf.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MediaStoreBridge(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun publishToMediaStore(
        sourceFile: File,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(collection, values) ?: return@withContext null

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(uri, completeValues, null, null)
            }
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    suspend fun deleteMediaStoreItem(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyFileDeleted(file: File): Boolean = withContext(Dispatchers.IO) {
        !file.exists()
    }
}
