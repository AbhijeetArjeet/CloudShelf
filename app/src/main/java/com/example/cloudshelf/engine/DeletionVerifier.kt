package com.example.cloudshelf.engine

import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.model.BatchState
import com.example.cloudshelf.model.MediaItem
import com.example.cloudshelf.model.MediaState
import com.example.cloudshelf.storage.MediaStoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeletionVerifier(
    private val db: CloudShelfDb,
    private val mediaStoreBridge: MediaStoreBridge
) {
    /**
     * Verifies that local staging files for the confirmed batch are completely removed.
     * Throws an exception if any staging file still persists.
     */
    suspend fun verifyAndCleanupBatch(batchId: String, stagingFolder: File): Boolean = withContext(Dispatchers.IO) {
        val items = db.getMediaItemsForBatch(batchId)
        var anyFailed = false

        for (item in items) {
            // Delete physical local file
            if (!item.localUri.isNullOrBlank()) {
                val file = File(item.localUri)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        anyFailed = true
                    }
                }
            }

            // Delete MediaStore entry
            if (!item.mediaStoreUri.isNullOrBlank()) {
                val uri = android.net.Uri.parse(item.mediaStoreUri)
                mediaStoreBridge.deleteMediaStoreItem(uri)
            }

            // Update item state to DELETED_LOCAL
            db.updateMediaItemState(item.id, MediaState.DELETED_LOCAL)
        }

        // Clean up empty batch directory if empty
        if (stagingFolder.exists() && stagingFolder.isDirectory) {
            val remaining = stagingFolder.listFiles()
            if (remaining.isNullOrEmpty()) {
                stagingFolder.delete()
            } else {
                anyFailed = true
            }
        }

        !anyFailed
    }
}
