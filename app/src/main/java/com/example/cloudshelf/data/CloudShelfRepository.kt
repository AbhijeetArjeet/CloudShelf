package com.example.cloudshelf.data

import android.content.Context
import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.deduplication.DeduplicationEngine
import com.example.cloudshelf.engine.BatchPlan
import com.example.cloudshelf.engine.DeletionVerifier
import com.example.cloudshelf.engine.StagingEngine
import com.example.cloudshelf.model.*
import com.example.cloudshelf.providers.MediaSourceProvider
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.storage.MediaStoreBridge
import com.example.cloudshelf.storage.StorageGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CloudShelfRepository(private val context: Context) {

    val db = CloudShelfDb.getInstance(context)
    val storageGuard = StorageGuard(context)
    val mediaStoreBridge = MediaStoreBridge(context)
    val dedupeEngine = DeduplicationEngine(db)
    val deletionVerifier = DeletionVerifier(db, mediaStoreBridge)
    val stagingEngine = StagingEngine(context, db, storageGuard, dedupeEngine, mediaStoreBridge, deletionVerifier)
    val deviceSyncManager = com.example.cloudshelf.sync.DeviceSyncManager(context, db, storageGuard, mediaStoreBridge)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _activeBatch = MutableStateFlow<StagingBatch?>(null)
    val activeBatch: StateFlow<StagingBatch?> = _activeBatch.asStateFlow()

    private val _storageStatus = MutableStateFlow<StorageStatus?>(null)
    val storageStatus: StateFlow<StorageStatus?> = _storageStatus.asStateFlow()

    private val _settings = MutableStateFlow(CloudShelfSettings())
    val settings: StateFlow<CloudShelfSettings> = _settings.asStateFlow()

    private val _allBatches = MutableStateFlow<List<StagingBatch>>(emptyList())
    val allBatches: StateFlow<List<StagingBatch>> = _allBatches.asStateFlow()

    private val _allFailures = MutableStateFlow<List<FailureRecord>>(emptyList())
    val allFailures: StateFlow<List<FailureRecord>> = _allFailures.asStateFlow()

    private val _allAccounts = MutableStateFlow<List<Account>>(emptyList())
    val allAccounts: StateFlow<List<Account>> = _allAccounts.asStateFlow()

    private val _currentProgress = MutableStateFlow<Triple<Int, Int, String>?>(null)
    val currentProgress: StateFlow<Triple<Int, Int, String>?> = _currentProgress.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        scope.launch {
            loadSettings()
            refreshStorage()
            refreshActiveBatch()
            refreshBatches()
            refreshFailures()
            refreshAccounts()
        }
    }

    suspend fun refreshStorage() = withContext(Dispatchers.IO) {
        val status = storageGuard.getStorageStatus(_settings.value.minFreeReserveBytes)
        _storageStatus.value = status
    }

    suspend fun refreshActiveBatch() = withContext(Dispatchers.IO) {
        _activeBatch.value = db.getActiveBatch()
    }

    suspend fun refreshBatches() = withContext(Dispatchers.IO) {
        _allBatches.value = db.getAllBatches()
    }

    suspend fun refreshFailures() = withContext(Dispatchers.IO) {
        _allFailures.value = db.getAllFailures()
    }

    suspend fun refreshAccounts() = withContext(Dispatchers.IO) {
        _allAccounts.value = db.getAllAccounts()
    }

    suspend fun confirmActiveBatch() = withContext(Dispatchers.IO) {
        val active = _activeBatch.value ?: return@withContext
        val updated = stagingEngine.confirmBackup(active.id, ConfirmationMethod.USER_MANUAL)
        _activeBatch.value = updated
        refreshBatches()
    }

    suspend fun deleteActiveBatchLocal() = withContext(Dispatchers.IO) {
        val active = _activeBatch.value ?: return@withContext
        val updated = stagingEngine.deleteLocalStaging(active.id)
        _activeBatch.value = if (updated.state == BatchState.COMPLETED) null else updated
        refreshBatches()
        refreshStorage()
    }

    suspend fun runImportPipeline(
        summary: ScanSummary,
        provider: MediaSourceProvider
    ) = withContext(Dispatchers.IO) {
        val stagingLimit = _settings.value.stagingLimitBytes
        val plans = stagingEngine.createBatchPlans(summary.items, stagingLimit)

        val jobId = UUID.randomUUID().toString()
        val job = ImportJob(
            id = jobId,
            sourceType = summary.sourceType,
            sourceLocation = summary.sourceName,
            status = JobStatus.IN_PROGRESS,
            totalBytesEstimated = summary.totalSizeBytes,
            totalItemsEstimated = summary.totalItems,
            batchesTotal = plans.size
        )
        db.insertOrUpdateJob(job)

        for (plan in plans) {
            val batch = stagingEngine.stageBatch(
                jobId = jobId,
                batchPlan = plan,
                totalBatches = plans.size,
                provider = provider,
                minReserveBytes = _settings.value.minFreeReserveBytes
            ) { curr, tot, file ->
                _currentProgress.value = Triple(curr, tot, file)
            }

            _activeBatch.value = batch
            refreshBatches()
            refreshStorage()

            // In Manual or Semi-Auto mode, pause here for user backup verification
            if (batch.state == BatchState.READY_FOR_BACKUP) {
                break
            }
        }
        _currentProgress.value = null
    }

    suspend fun updateSettings(newSettings: CloudShelfSettings) = withContext(Dispatchers.IO) {
        _settings.value = newSettings
        db.setSetting("stagingLimitBytes", newSettings.stagingLimitBytes.toString())
        db.setSetting("minFreeReserveBytes", newSettings.minFreeReserveBytes.toString())
        db.setSetting("backupAccountEmail", newSettings.backupAccountEmail)
        db.setSetting("verificationMode", newSettings.verificationMode.name)
        db.setSetting("duplicatePolicy", newSettings.duplicatePolicy.name)
        refreshStorage()
    }

    private suspend fun loadSettings() = withContext(Dispatchers.IO) {
        val limit = db.getSetting("stagingLimitBytes", (3L * 1024 * 1024 * 1024).toString()).toLongOrNull() ?: (3L * 1024 * 1024 * 1024)
        val reserve = db.getSetting("minFreeReserveBytes", (5L * 1024 * 1024 * 1024).toString()).toLongOrNull() ?: (5L * 1024 * 1024 * 1024)
        val email = db.getSetting("backupAccountEmail", "")
        val modeStr = db.getSetting("verificationMode", VerificationMode.MANUAL.name)
        val mode = try { VerificationMode.valueOf(modeStr) } catch (e: Exception) { VerificationMode.MANUAL }
        val dupStr = db.getSetting("duplicatePolicy", DuplicatePolicy.KEEP_FIRST.name)
        val dup = try { DuplicatePolicy.valueOf(dupStr) } catch (e: Exception) { DuplicatePolicy.KEEP_FIRST }

        _settings.value = CloudShelfSettings(
            stagingLimitBytes = limit,
            minFreeReserveBytes = reserve,
            backupAccountEmail = email,
            verificationMode = mode,
            duplicatePolicy = dup
        )
    }

    suspend fun addAccount(email: String, name: String) = withContext(Dispatchers.IO) {
        val account = Account(
            id = UUID.randomUUID().toString(),
            email = email,
            displayName = name,
            authState = AuthState.CONNECTED
        )
        db.insertOrUpdateAccount(account)
        refreshAccounts()
    }

    suspend fun removeAccount(id: String) = withContext(Dispatchers.IO) {
        db.deleteAccount(id)
        refreshAccounts()
    }

    suspend fun clearAllFailures() = withContext(Dispatchers.IO) {
        db.clearFailures()
        refreshFailures()
    }
}
