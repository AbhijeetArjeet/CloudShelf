package com.example.cloudshelf.sync

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.model.TransferManifestItem
import com.example.cloudshelf.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class SourceMediaObserver(
    private val context: Context,
    private val db: CloudShelfDb,
    private val onNewMediaQueued: (List<TransferManifestItem>) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastCheckedTimestampSec = System.currentTimeMillis() / 1000

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scanIncrementalNewMedia()
        }
    }

    fun startObserving() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    fun stopObserving() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun scanIncrementalNewMedia() {
        scope.launch {
            val pairedDevices = db.getAllPairedDevices()
            if (pairedDevices.isEmpty()) return@launch
            val targetDevice = pairedDevices.first()

            val newItems = mutableListOf<TransferManifestItem>()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
            )
            val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
            val selectionArgs = arrayOf(lastCheckedTimestampSec.toString())

            val uris = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )

            for (collectionUri in uris) {
                context.contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "photo_$mediaId.jpg"
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val size = cursor.getLong(sizeCol)
                        val dateAdded = cursor.getLong(dateCol)

                        if (dateAdded > lastCheckedTimestampSec) {
                            lastCheckedTimestampSec = dateAdded
                        }

                        val manifestItem = TransferManifestItem(
                            id = UUID.randomUUID().toString(),
                            deviceId = targetDevice.id,
                            sourceUri = Uri.withAppendedPath(collectionUri, mediaId.toString()).toString(),
                            filename = name,
                            mimeType = mime,
                            sizeBytes = size,
                            status = TransferStatus.QUEUED
                        )
                        db.insertOrUpdateManifestItem(manifestItem)
                        newItems.add(manifestItem)
                    }
                }
            }

            if (newItems.isNotEmpty()) {
                onNewMediaQueued(newItems)
            }
        }
    }
}
