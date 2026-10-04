package com.example.cloudshelf.engine

import android.content.Context
import android.os.Environment
import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.deduplication.DeduplicationEngine
import com.example.cloudshelf.deduplication.DeduplicationResult
import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.model.*
import com.example.cloudshelf.providers.MediaSourceProvider
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.providers.SourceItemRef
import com.example.cloudshelf.storage.MediaStoreBridge
import com.example.cloudshelf.storage.SafeFileIO
import com.example.cloudshelf.storage.StorageGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class BatchPlan(
    val batchIndex: Int,
    val items: List<SourceItemRef>,
    val totalSizeBytes: Long,
    val estimatedItemCount: Int
)

class StagingEngine(
    private val context: Context,
    private val db: CloudShelfDb,
    private val storageGuard: StorageGuard,
    private val dedupeEngine: DeduplicationEngine,
    private val mediaStoreBridge: MediaStoreBridge,
    private val deletionVerifier: DeletionVerifier
) {

    /**
     * Requirement 21 & 22: Batch planning with strict target thresholds.
     * Large files exceeding threshold are placed in their own single-file batch.
     */
    fun createBatchPlans(items: List<SourceItemRef>, stagingLimitBytes: Long): List<BatchPlan> {
        val plans = mutableListOf<BatchPlan>()
        var currentBatchItems = mutableListOf<SourceItemRef>()
        var currentBatchBytes = 0L
        var batchIndex = 1

        for (item in items) {
            val itemSize = item.estimatedSizeBytes

            // Exception: individual file larger than configured batch size gets its own batch
            if (itemSize >= stagingLimitBytes) {
                if (currentBatchItems.isNotEmpty()) {
                    plans.add(BatchPlan(batchIndex++, currentBatchItems, currentBatchBytes, currentBatchItems.size))
                    currentBatchItems = mutableListOf()
                    currentBatchBytes = 0L
                }
                plans.add(BatchPlan(batchIndex++, listOf(item), itemSize, 1))
                continue
            }

            // If adding item would exceed limit, finalize current batch and start new one
            if (currentBatchBytes + itemSize > stagingLimitBytes && currentBatchItems.isNotEmpty()) {
                plans.add(BatchPlan(batchIndex++, currentBatchItems, currentBatchBytes, currentBatchItems.size))
                currentBatchItems = mutableListOf()
                currentBatchBytes = 0L
            }

            currentBatchItems.add(item)
            currentBatchBytes += itemSize
        }

        if (currentBatchItems.isNotEmpty()) {
            plans.add(BatchPlan(batchIndex, currentBatchItems, currentBatchBytes, currentBatchItems.size))
        }

        return plans
    }

    /**
     * Executes staging of a planned batch onto the device.
     */
    suspend fun stageBatch(
        jobId: String,
        batchPlan: BatchPlan,
        totalBatches: Int,
        provider: MediaSourceProvider,
        minReserveBytes: Long,
        onProgress: (current: Int, total: Int, currentFilename: String) -> Unit
    ): StagingBatch = withContext(Dispatchers.IO) {
        // Dynamic storage check (Requirements 23 & 24)
        val canStage = storageGuard.canSafelyStage(batchPlan.totalSizeBytes, minReserveBytes)
        if (!canStage) {
            val status = storageGuard.getStorageStatus(minReserveBytes)
            val pausedBatch = StagingBatch(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                batchIndex = batchPlan.batchIndex,
                totalBatchesEstimated = totalBatches,
                state = BatchState.PAUSED,
                targetSizeBytes = batchPlan.totalSizeBytes,
                actualSizeBytes = 0L,
                itemCount = batchPlan.items.size,
                stagingFolder = "",
                errorMessage = "Paused: staging would leave less than ${minReserveBytes / (1024 * 1024 * 1024)} GB free storage."
            )
            db.insertOrUpdateBatch(pausedBatch)
            return@withContext pausedBatch
        }

        val stagingDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CloudShelf/Staging/Batch-${String.format("%03d", batchPlan.batchIndex)}"
        )
        if (!stagingDir.exists()) stagingDir.mkdirs()

        val batchId = UUID.randomUUID().toString()
        var batch = StagingBatch(
            id = batchId,
            jobId = jobId,
            batchIndex = batchPlan.batchIndex,
            totalBatchesEstimated = totalBatches,
            state = BatchState.IMPORTING,
            targetSizeBytes = batchPlan.totalSizeBytes,
            stagingFolder = stagingDir.absolutePath,
            importedAt = System.currentTimeMillis()
        )
        db.insertOrUpdateBatch(batch)

        var totalStagedBytes = 0L
        var processedCount = 0

        for ((index, itemRef) in batchPlan.items.withIndex()) {
            onProgress(index + 1, batchPlan.items.size, itemRef.filename)

            try {
                val targetFile = SafeFileIO.resolveFilenameCollision(stagingDir, itemRef.filename)

                // Atomic streaming write
                provider.openItemStream(itemRef).use { inputStream ->
                    SafeFileIO.atomicWrite(targetFile, inputStream, itemRef.estimatedSizeBytes)
                }

                val actualSize = targetFile.length()
                totalStagedBytes += actualSize

                // Streaming SHA-256 deduplication
                val dedupeResult = dedupeEngine.checkDuplicate(
                    file = targetFile,
                    sizeBytes = actualSize,
                    mimeType = itemRef.mimeType,
                    sourceType = provider.sourceType,
                    sourceIdentifier = itemRef.identifier,
                    sourceAccountEmail = null
                )

                val sha256 = when (dedupeResult) {
                    is DeduplicationResult.Unique -> {
                        dedupeEngine.registerUniqueHash(
                            hash = dedupeResult.sha256Hash,
                            sizeBytes = actualSize,
                            mimeType = itemRef.mimeType,
                            mediaItemId = itemRef.identifier
                        )
                        dedupeResult.sha256Hash
                    }
                    is DeduplicationResult.Duplicate -> dedupeResult.existingHash
                }

                // Publish to Android MediaStore for Google Photos visibility
                val relativePath = "Pictures/CloudShelf/Staging/Batch-${String.format("%03d", batchPlan.batchIndex)}/"
                val mediaStoreUri = mediaStoreBridge.publishToMediaStore(
                    sourceFile = targetFile,
                    mimeType = itemRef.mimeType,
                    relativePath = relativePath,
                    displayName = targetFile.name
                )

                val mediaItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    batchId = batchId,
                    sourceType = provider.sourceType,
                    sourceIdentifier = itemRef.identifier,
                    originalFilename = itemRef.filename,
                    normalizedFilename = targetFile.name,
                    stagingRelativePath = relativePath,
                    mimeType = itemRef.mimeType,
                    sizeBytes = actualSize,
                    sha256Hash = sha256,
                    state = MediaState.LOCAL_VERIFIED,
                    localUri = targetFile.absolutePath,
                    mediaStoreUri = mediaStoreUri?.toString(),
                    verifiedAt = System.currentTimeMillis(),
                    title = itemRef.sidecarMetadata?.title,
                    description = itemRef.sidecarMetadata?.description,
                    latitude = itemRef.sidecarMetadata?.latitude,
                    longitude = itemRef.sidecarMetadata?.longitude
                )
                db.insertMediaItem(mediaItem)
                processedCount++
            } catch (e: Exception) {
                db.insertFailure(
                    FailureRecord(
                        id = UUID.randomUUID().toString(),
                        jobId = jobId,
                        batchId = batchId,
                        filename = itemRef.filename,
                        errorCode = "STAGE_ERROR",
                        userMessage = "Could not write staging file: ${e.message}",
                        technicalDetails = e.stackTraceToString(),
                        canRetry = true
                    )
                )
            }
        }

        batch = batch.copy(
            state = BatchState.READY_FOR_BACKUP,
            actualSizeBytes = totalStagedBytes,
            itemCount = processedCount
        )
        db.insertOrUpdateBatch(batch)
        batch
    }

    /**
     * User confirms backup in Google Photos. Unlocks deletion.
     */
    suspend fun confirmBackup(batchId: String, method: ConfirmationMethod = ConfirmationMethod.USER_MANUAL): StagingBatch = withContext(Dispatchers.IO) {
        val existing = db.getBatch(batchId) ?: throw IllegalArgumentException("Batch not found: $batchId")
        val updated = existing.copy(
            state = BatchState.BACKUP_CONFIRMED,
            backupConfirmedAt = System.currentTimeMillis(),
            confirmationMethod = method
        )
        db.insertOrUpdateBatch(updated)
        updated
    }

    /**
     * Requirement 27 & 35: Strict Golden Safety Rule.
     * Deletes local staging copies ONLY after backup is explicitly confirmed.
     */
    suspend fun deleteLocalStaging(batchId: String): StagingBatch = withContext(Dispatchers.IO) {
        val batch = db.getBatch(batchId) ?: throw IllegalArgumentException("Batch not found: $batchId")

        // CRITICAL DATA-SAFETY GUARD
        if (batch.state != BatchState.BACKUP_CONFIRMED) {
            throw SecurityException("Golden Safety Rule Violation: Cannot delete local staging copy before backup is confirmed.")
        }

        var inProgress = batch.copy(state = BatchState.DELETING_LOCAL)
        db.insertOrUpdateBatch(inProgress)

        val stagingFolder = File(batch.stagingFolder)
        val success = deletionVerifier.verifyAndCleanupBatch(batchId, stagingFolder)

        val finalState = if (success) BatchState.COMPLETED else BatchState.FAILED
        val completedBatch = inProgress.copy(
            state = finalState,
            localDeletedAt = System.currentTimeMillis(),
            errorMessage = if (!success) "Local deletion could not verify all files removed." else null
        )
        db.insertOrUpdateBatch(completedBatch)
        completedBatch
    }
}
