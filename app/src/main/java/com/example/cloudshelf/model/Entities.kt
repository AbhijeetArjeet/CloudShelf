package com.example.cloudshelf.model

data class MediaItem(
    val id: String,
    val jobId: String,
    val batchId: String,
    val sourceType: SourceType,
    val sourceIdentifier: String,
    val sourceAccountId: String? = null,
    val originalFilename: String,
    val normalizedFilename: String,
    val stagingRelativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hash: String? = null,
    val state: MediaState = MediaState.PENDING,
    val localUri: String? = null,
    val mediaStoreUri: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val createdAtDevice: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class StagingBatch(
    val id: String,
    val jobId: String,
    val batchIndex: Int,
    val totalBatchesEstimated: Int,
    val state: BatchState = BatchState.CREATED,
    val targetSizeBytes: Long,
    val actualSizeBytes: Long = 0L,
    val itemCount: Int = 0,
    val stagingFolder: String,
    val importedAt: Long? = null,
    val backupConfirmedAt: Long? = null,
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.NONE,
    val localDeletedAt: Long? = null,
    val errorMessage: String? = null
)

data class ImportJob(
    val id: String,
    val sourceType: SourceType,
    val sourceLocation: String,
    val sourceAccountId: String? = null,
    val status: JobStatus = JobStatus.CREATED,
    val totalBytesEstimated: Long = 0L,
    val totalItemsEstimated: Int = 0,
    val importedBytes: Long = 0L,
    val importedItems: Int = 0,
    val batchesTotal: Int = 0,
    val currentBatchIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
)

data class Account(
    val id: String,
    val email: String,
    val displayName: String,
    val authState: AuthState = AuthState.CONNECTED,
    val lastImportAt: Long? = null
)

data class HashRecord(
    val sha256Hash: String,
    val sizeBytes: Long,
    val mimeType: String,
    val firstMediaItemId: String,
    val firstSeenAt: Long = System.currentTimeMillis()
)

data class Provenance(
    val id: String,
    val mediaItemId: String,
    val sourceType: SourceType,
    val sourceIdentifier: String,
    val sourceAccountEmail: String? = null,
    val discoveredAt: Long = System.currentTimeMillis()
)

data class FailureRecord(
    val id: String,
    val jobId: String? = null,
    val batchId: String? = null,
    val filename: String,
    val errorCode: String,
    val userMessage: String,
    val technicalDetails: String,
    val canRetry: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class CloudShelfSettings(
    val stagingLimitBytes: Long = 3L * 1024 * 1024 * 1024, // 3 GB default
    val minFreeReserveBytes: Long = 5L * 1024 * 1024 * 1024, // 5 GB reserve
    val backupAccountEmail: String = "",
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val verificationMode: VerificationMode = VerificationMode.MANUAL,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.KEEP_FIRST,
    val stagingDirectoryName: String = "Pictures/CloudShelf/Staging",
    val permanentDirectoryName: String = "Pictures/CloudShelf/Permanent"
)

data class StorageStatus(
    val totalBytes: Long,
    val freeBytes: Long,
    val stagingUsageBytes: Long,
    val permanentUsageBytes: Long,
    val minReserveBytes: Long,
    val safeAvailableBytes: Long,
    val isSafeForNextBatch: Boolean
)

data class PairedDevice(
    val id: String,
    val deviceName: String,
    val role: DeviceRole,
    val endpointHost: String,
    val endpointPort: Int,
    val authToken: String,
    val pairingStatus: PairingStatus = PairingStatus.PAIRED,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

data class TransferManifestItem(
    val id: String,
    val deviceId: String,
    val sourceUri: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytesTransferred: Long = 0L,
    val sha256Hash: String? = null,
    val status: TransferStatus = TransferStatus.QUEUED,
    val retryCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class DeviceCapacityInfo(
    val stagingLimitBytes: Long,
    val stagedBytes: Long,
    val safeAvailableBytes: Long,
    val stagingFolder: String,
    val isAcceptingTransfers: Boolean,
    val statusMessage: String
)

data class PairingPayload(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val pairingPin: String,
    val pairingToken: String
)
