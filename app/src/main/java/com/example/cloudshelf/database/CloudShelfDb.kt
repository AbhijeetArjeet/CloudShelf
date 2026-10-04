package com.example.cloudshelf.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.cloudshelf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloudShelfDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "cloudshelf.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: CloudShelfDb? = null

        fun getInstance(context: Context): CloudShelfDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudShelfDb(context).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS accounts (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                display_name TEXT NOT NULL,
                auth_state TEXT NOT NULL,
                last_import_at INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS import_jobs (
                id TEXT PRIMARY KEY,
                source_type TEXT NOT NULL,
                source_location TEXT NOT NULL,
                source_account_id TEXT,
                status TEXT NOT NULL,
                total_bytes_estimated INTEGER NOT NULL,
                total_items_estimated INTEGER NOT NULL,
                imported_bytes INTEGER NOT NULL,
                imported_items INTEGER NOT NULL,
                batches_total INTEGER NOT NULL,
                current_batch_index INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                completed_at INTEGER,
                error_message TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS staging_batches (
                id TEXT PRIMARY KEY,
                job_id TEXT NOT NULL,
                batch_index INTEGER NOT NULL,
                total_batches_estimated INTEGER NOT NULL,
                state TEXT NOT NULL,
                target_size_bytes INTEGER NOT NULL,
                actual_size_bytes INTEGER NOT NULL,
                item_count INTEGER NOT NULL,
                staging_folder TEXT NOT NULL,
                imported_at INTEGER,
                backup_confirmed_at INTEGER,
                confirmation_method TEXT NOT NULL,
                local_deleted_at INTEGER,
                error_message TEXT,
                FOREIGN KEY (job_id) REFERENCES import_jobs(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS media_items (
                id TEXT PRIMARY KEY,
                job_id TEXT NOT NULL,
                batch_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_identifier TEXT NOT NULL,
                source_account_id TEXT,
                original_filename TEXT NOT NULL,
                normalized_filename TEXT NOT NULL,
                staging_relative_path TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                sha256_hash TEXT,
                state TEXT NOT NULL,
                local_uri TEXT,
                media_store_uri TEXT,
                width INTEGER,
                height INTEGER,
                duration_ms INTEGER,
                created_at_device INTEGER NOT NULL,
                verified_at INTEGER,
                title TEXT,
                description TEXT,
                latitude REAL,
                longitude REAL,
                FOREIGN KEY (batch_id) REFERENCES staging_batches(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hash_records (
                sha256_hash TEXT PRIMARY KEY,
                size_bytes INTEGER NOT NULL,
                mime_type TEXT NOT NULL,
                first_media_item_id TEXT NOT NULL,
                first_seen_at INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS provenance (
                id TEXT PRIMARY KEY,
                media_item_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_identifier TEXT NOT NULL,
                source_account_email TEXT,
                discovered_at INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS failure_records (
                id TEXT PRIMARY KEY,
                job_id TEXT,
                batch_id TEXT,
                filename TEXT NOT NULL,
                error_code TEXT NOT NULL,
                user_message TEXT NOT NULL,
                technical_details TEXT NOT NULL,
                can_retry INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS paired_devices (
                id TEXT PRIMARY KEY,
                device_name TEXT NOT NULL,
                role TEXT NOT NULL,
                endpoint_host TEXT NOT NULL,
                endpoint_port INTEGER NOT NULL,
                auth_token TEXT NOT NULL,
                pairing_status TEXT NOT NULL,
                last_seen_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transfer_manifest (
                id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                source_uri TEXT NOT NULL,
                filename TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                bytes_transferred INTEGER NOT NULL,
                sha256_hash TEXT,
                status TEXT NOT NULL,
                retry_count INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)

        // Indexes for performance on 100k+ items
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_batch ON media_items(batch_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_job ON media_items(job_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_hash ON media_items(sha256_hash)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_state ON media_items(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_batch_job ON staging_batches(job_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_batch_state ON staging_batches(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_provenance_item ON provenance(media_item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_manifest_device ON transfer_manifest(device_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_manifest_status ON transfer_manifest(status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future upgrades
    }

    // ==========================================
    // MediaItem Operations
    // ==========================================

    suspend fun insertMediaItem(item: MediaItem) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("media_items", null, ContentValues().apply {
            put("id", item.id)
            put("job_id", item.jobId)
            put("batch_id", item.batchId)
            put("source_type", item.sourceType.name)
            put("source_identifier", item.sourceIdentifier)
            put("source_account_id", item.sourceAccountId)
            put("original_filename", item.originalFilename)
            put("normalized_filename", item.normalizedFilename)
            put("staging_relative_path", item.stagingRelativePath)
            put("mime_type", item.mimeType)
            put("size_bytes", item.sizeBytes)
            put("sha256_hash", item.sha256Hash)
            put("state", item.state.name)
            put("local_uri", item.localUri)
            put("media_store_uri", item.mediaStoreUri)
            put("width", item.width)
            put("height", item.height)
            put("duration_ms", item.durationMs)
            put("created_at_device", item.createdAtDevice)
            put("verified_at", item.verifiedAt)
            put("title", item.title)
            put("description", item.description)
            put("latitude", item.latitude)
            put("longitude", item.longitude)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun updateMediaItemState(id: String, state: MediaState, verifiedAt: Long? = null) = withContext(Dispatchers.IO) {
        writableDatabase.update("media_items", ContentValues().apply {
            put("state", state.name)
            if (verifiedAt != null) put("verified_at", verifiedAt)
        }, "id = ?", arrayOf(id))
    }

    suspend fun getMediaItemsForBatch(batchId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        readableDatabase.rawQuery("SELECT * FROM media_items WHERE batch_id = ? ORDER BY original_filename ASC", arrayOf(batchId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToMediaItem(cursor))
            }
        }
        list
    }

    suspend fun getMediaItemCountForBatch(batchId: String): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM media_items WHERE batch_id = ?", arrayOf(batchId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun cursorToMediaItem(c: Cursor): MediaItem {
        return MediaItem(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            jobId = c.getString(c.getColumnIndexOrThrow("job_id")),
            batchId = c.getString(c.getColumnIndexOrThrow("batch_id")),
            sourceType = SourceType.valueOf(c.getString(c.getColumnIndexOrThrow("source_type"))),
            sourceIdentifier = c.getString(c.getColumnIndexOrThrow("source_identifier")),
            sourceAccountId = c.getString(c.getColumnIndexOrThrow("source_account_id")),
            originalFilename = c.getString(c.getColumnIndexOrThrow("original_filename")),
            normalizedFilename = c.getString(c.getColumnIndexOrThrow("normalized_filename")),
            stagingRelativePath = c.getString(c.getColumnIndexOrThrow("staging_relative_path")),
            mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
            sizeBytes = c.getLong(c.getColumnIndexOrThrow("size_bytes")),
            sha256Hash = c.getString(c.getColumnIndexOrThrow("sha256_hash")),
            state = MediaState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
            localUri = c.getString(c.getColumnIndexOrThrow("local_uri")),
            mediaStoreUri = c.getString(c.getColumnIndexOrThrow("media_store_uri")),
            width = if (c.isNull(c.getColumnIndexOrThrow("width"))) null else c.getInt(c.getColumnIndexOrThrow("width")),
            height = if (c.isNull(c.getColumnIndexOrThrow("height"))) null else c.getInt(c.getColumnIndexOrThrow("height")),
            durationMs = if (c.isNull(c.getColumnIndexOrThrow("duration_ms"))) null else c.getLong(c.getColumnIndexOrThrow("duration_ms")),
            createdAtDevice = c.getLong(c.getColumnIndexOrThrow("created_at_device")),
            verifiedAt = if (c.isNull(c.getColumnIndexOrThrow("verified_at"))) null else c.getLong(c.getColumnIndexOrThrow("verified_at")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            latitude = if (c.isNull(c.getColumnIndexOrThrow("latitude"))) null else c.getDouble(c.getColumnIndexOrThrow("latitude")),
            longitude = if (c.isNull(c.getColumnIndexOrThrow("longitude"))) null else c.getDouble(c.getColumnIndexOrThrow("longitude"))
        )
    }

    // ==========================================
    // StagingBatch Operations
    // ==========================================

    suspend fun insertOrUpdateBatch(batch: StagingBatch) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("staging_batches", null, ContentValues().apply {
            put("id", batch.id)
            put("job_id", batch.jobId)
            put("batch_index", batch.batchIndex)
            put("total_batches_estimated", batch.totalBatchesEstimated)
            put("state", batch.state.name)
            put("target_size_bytes", batch.targetSizeBytes)
            put("actual_size_bytes", batch.actualSizeBytes)
            put("item_count", batch.itemCount)
            put("staging_folder", batch.stagingFolder)
            put("imported_at", batch.importedAt)
            put("backup_confirmed_at", batch.backupConfirmedAt)
            put("confirmation_method", batch.confirmationMethod.name)
            put("local_deleted_at", batch.localDeletedAt)
            put("error_message", batch.errorMessage)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getBatch(batchId: String): StagingBatch? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM staging_batches WHERE id = ?", arrayOf(batchId)).use { cursor ->
            if (cursor.moveToFirst()) cursorToBatch(cursor) else null
        }
    }

    suspend fun getActiveBatch(): StagingBatch? = withContext(Dispatchers.IO) {
        val activeStates = arrayOf(
            BatchState.CREATED.name, BatchState.SCANNING.name, BatchState.IMPORTING.name,
            BatchState.LOCAL_VERIFIED.name, BatchState.READY_FOR_BACKUP.name,
            BatchState.BACKUP_PENDING.name, BatchState.BACKUP_CONFIRMED.name,
            BatchState.DELETING_LOCAL.name, BatchState.PAUSED.name
        )
        val placeholders = activeStates.joinToString(",") { "?" }
        readableDatabase.rawQuery("SELECT * FROM staging_batches WHERE state IN ($placeholders) ORDER BY batch_index ASC LIMIT 1", activeStates).use { cursor ->
            if (cursor.moveToFirst()) cursorToBatch(cursor) else null
        }
    }

    suspend fun getAllBatches(): List<StagingBatch> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StagingBatch>()
        readableDatabase.rawQuery("SELECT * FROM staging_batches ORDER BY batch_index DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToBatch(cursor))
            }
        }
        list
    }

    private fun cursorToBatch(c: Cursor): StagingBatch {
        return StagingBatch(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            jobId = c.getString(c.getColumnIndexOrThrow("job_id")),
            batchIndex = c.getInt(c.getColumnIndexOrThrow("batch_index")),
            totalBatchesEstimated = c.getInt(c.getColumnIndexOrThrow("total_batches_estimated")),
            state = BatchState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
            targetSizeBytes = c.getLong(c.getColumnIndexOrThrow("target_size_bytes")),
            actualSizeBytes = c.getLong(c.getColumnIndexOrThrow("actual_size_bytes")),
            itemCount = c.getInt(c.getColumnIndexOrThrow("item_count")),
            stagingFolder = c.getString(c.getColumnIndexOrThrow("staging_folder")),
            importedAt = if (c.isNull(c.getColumnIndexOrThrow("imported_at"))) null else c.getLong(c.getColumnIndexOrThrow("imported_at")),
            backupConfirmedAt = if (c.isNull(c.getColumnIndexOrThrow("backup_confirmed_at"))) null else c.getLong(c.getColumnIndexOrThrow("backup_confirmed_at")),
            confirmationMethod = ConfirmationMethod.valueOf(c.getString(c.getColumnIndexOrThrow("confirmation_method"))),
            localDeletedAt = if (c.isNull(c.getColumnIndexOrThrow("local_deleted_at"))) null else c.getLong(c.getColumnIndexOrThrow("local_deleted_at")),
            errorMessage = c.getString(c.getColumnIndexOrThrow("error_message"))
        )
    }

    // ==========================================
    // ImportJob Operations
    // ==========================================

    suspend fun insertOrUpdateJob(job: ImportJob) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("import_jobs", null, ContentValues().apply {
            put("id", job.id)
            put("source_type", job.sourceType.name)
            put("source_location", job.sourceLocation)
            put("source_account_id", job.sourceAccountId)
            put("status", job.status.name)
            put("total_bytes_estimated", job.totalBytesEstimated)
            put("total_items_estimated", job.totalItemsEstimated)
            put("imported_bytes", job.importedBytes)
            put("imported_items", job.importedItems)
            put("batches_total", job.batchesTotal)
            put("current_batch_index", job.currentBatchIndex)
            put("created_at", job.createdAt)
            put("completed_at", job.completedAt)
            put("error_message", job.errorMessage)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getJob(id: String): ImportJob? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM import_jobs WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursorToJob(cursor) else null
        }
    }

    suspend fun getActiveJob(): ImportJob? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM import_jobs WHERE status IN (?, ?) ORDER BY created_at DESC LIMIT 1", arrayOf(JobStatus.IN_PROGRESS.name, JobStatus.CREATED.name)).use { cursor ->
            if (cursor.moveToFirst()) cursorToJob(cursor) else null
        }
    }

    private fun cursorToJob(c: Cursor): ImportJob {
        return ImportJob(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            sourceType = SourceType.valueOf(c.getString(c.getColumnIndexOrThrow("source_type"))),
            sourceLocation = c.getString(c.getColumnIndexOrThrow("source_location")),
            sourceAccountId = c.getString(c.getColumnIndexOrThrow("source_account_id")),
            status = JobStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
            totalBytesEstimated = c.getLong(c.getColumnIndexOrThrow("total_bytes_estimated")),
            totalItemsEstimated = c.getInt(c.getColumnIndexOrThrow("total_items_estimated")),
            importedBytes = c.getLong(c.getColumnIndexOrThrow("imported_bytes")),
            importedItems = c.getInt(c.getColumnIndexOrThrow("imported_items")),
            batchesTotal = c.getInt(c.getColumnIndexOrThrow("batches_total")),
            currentBatchIndex = c.getInt(c.getColumnIndexOrThrow("current_batch_index")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            completedAt = if (c.isNull(c.getColumnIndexOrThrow("completed_at"))) null else c.getLong(c.getColumnIndexOrThrow("completed_at")),
            errorMessage = c.getString(c.getColumnIndexOrThrow("error_message"))
        )
    }

    // ==========================================
    // Hash & Deduplication Operations
    // ==========================================

    suspend fun findHashRecord(hash: String): HashRecord? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM hash_records WHERE sha256_hash = ?", arrayOf(hash)).use { cursor ->
            if (cursor.moveToFirst()) {
                HashRecord(
                    sha256Hash = cursor.getString(cursor.getColumnIndexOrThrow("sha256_hash")),
                    sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                    firstMediaItemId = cursor.getString(cursor.getColumnIndexOrThrow("first_media_item_id")),
                    firstSeenAt = cursor.getLong(cursor.getColumnIndexOrThrow("first_seen_at"))
                )
            } else null
        }
    }

    suspend fun insertHashRecord(record: HashRecord) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("hash_records", null, ContentValues().apply {
            put("sha256_hash", record.sha256Hash)
            put("size_bytes", record.sizeBytes)
            put("mime_type", record.mimeType)
            put("first_media_item_id", record.firstMediaItemId)
            put("first_seen_at", record.firstSeenAt)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    suspend fun insertProvenance(prov: Provenance) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("provenance", null, ContentValues().apply {
            put("id", prov.id)
            put("media_item_id", prov.mediaItemId)
            put("source_type", prov.sourceType.name)
            put("source_identifier", prov.sourceIdentifier)
            put("source_account_email", prov.sourceAccountEmail)
            put("discovered_at", prov.discoveredAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ==========================================
    // Accounts Operations
    // ==========================================

    suspend fun getAllAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Account>()
        readableDatabase.rawQuery("SELECT * FROM accounts ORDER BY email ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Account(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                        displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                        authState = AuthState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("auth_state"))),
                        lastImportAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("last_import_at"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("last_import_at"))
                    )
                )
            }
        }
        list
    }

    suspend fun insertOrUpdateAccount(account: Account) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("accounts", null, ContentValues().apply {
            put("id", account.id)
            put("email", account.email)
            put("display_name", account.displayName)
            put("auth_state", account.authState.name)
            put("last_import_at", account.lastImportAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteAccount(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("accounts", "id = ?", arrayOf(id))
    }

    // ==========================================
    // Failure Records
    // ==========================================

    suspend fun insertFailure(record: FailureRecord) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("failure_records", null, ContentValues().apply {
            put("id", record.id)
            put("job_id", record.jobId)
            put("batch_id", record.batchId)
            put("filename", record.filename)
            put("error_code", record.errorCode)
            put("user_message", record.userMessage)
            put("technical_details", record.technicalDetails)
            put("can_retry", if (record.canRetry) 1 else 0)
            put("timestamp", record.timestamp)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getAllFailures(): List<FailureRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FailureRecord>()
        readableDatabase.rawQuery("SELECT * FROM failure_records ORDER BY timestamp DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    FailureRecord(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        jobId = cursor.getString(cursor.getColumnIndexOrThrow("job_id")),
                        batchId = cursor.getString(cursor.getColumnIndexOrThrow("batch_id")),
                        filename = cursor.getString(cursor.getColumnIndexOrThrow("filename")),
                        errorCode = cursor.getString(cursor.getColumnIndexOrThrow("error_code")),
                        userMessage = cursor.getString(cursor.getColumnIndexOrThrow("user_message")),
                        technicalDetails = cursor.getString(cursor.getColumnIndexOrThrow("technical_details")),
                        canRetry = cursor.getInt(cursor.getColumnIndexOrThrow("can_retry")) == 1,
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        list
    }

    suspend fun deleteFailure(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("failure_records", "id = ?", arrayOf(id))
    }

    suspend fun clearFailures() = withContext(Dispatchers.IO) {
        writableDatabase.delete("failure_records", null, null)
    }

    // ==========================================
    // Settings Key-Value Store
    // ==========================================

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else defaultValue
        }
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key)
            put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ==========================================
    // Paired Devices Operations
    // ==========================================

    suspend fun insertOrUpdateDevice(device: PairedDevice) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("paired_devices", null, ContentValues().apply {
            put("id", device.id)
            put("device_name", device.deviceName)
            put("role", device.role.name)
            put("endpoint_host", device.endpointHost)
            put("endpoint_port", device.endpointPort)
            put("auth_token", device.authToken)
            put("pairing_status", device.pairingStatus.name)
            put("last_seen_at", device.lastSeenAt)
            put("created_at", device.createdAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getDevice(id: String): PairedDevice? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM paired_devices WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursorToDevice(cursor) else null
        }
    }

    suspend fun getAllPairedDevices(): List<PairedDevice> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PairedDevice>()
        readableDatabase.rawQuery("SELECT * FROM paired_devices WHERE pairing_status = ? ORDER BY created_at DESC", arrayOf(PairingStatus.PAIRED.name)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToDevice(cursor))
            }
        }
        list
    }

    suspend fun revokeDevice(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.update("paired_devices", ContentValues().apply {
            put("pairing_status", PairingStatus.REVOKED.name)
        }, "id = ?", arrayOf(id))
    }

    suspend fun deleteDevice(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("paired_devices", "id = ?", arrayOf(id))
    }

    private fun cursorToDevice(c: Cursor): PairedDevice {
        return PairedDevice(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            deviceName = c.getString(c.getColumnIndexOrThrow("device_name")),
            role = DeviceRole.valueOf(c.getString(c.getColumnIndexOrThrow("role"))),
            endpointHost = c.getString(c.getColumnIndexOrThrow("endpoint_host")),
            endpointPort = c.getInt(c.getColumnIndexOrThrow("endpoint_port")),
            authToken = c.getString(c.getColumnIndexOrThrow("auth_token")),
            pairingStatus = PairingStatus.valueOf(c.getString(c.getColumnIndexOrThrow("pairing_status"))),
            lastSeenAt = c.getLong(c.getColumnIndexOrThrow("last_seen_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }

    // ==========================================
    // Transfer Manifest Operations
    // ==========================================

    suspend fun insertOrUpdateManifestItem(item: TransferManifestItem) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict("transfer_manifest", null, ContentValues().apply {
            put("id", item.id)
            put("device_id", item.deviceId)
            put("source_uri", item.sourceUri)
            put("filename", item.filename)
            put("mime_type", item.mimeType)
            put("size_bytes", item.sizeBytes)
            put("bytes_transferred", item.bytesTransferred)
            put("sha256_hash", item.sha256Hash)
            put("status", item.status.name)
            put("retry_count", item.retryCount)
            put("updated_at", item.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getManifestItem(id: String): TransferManifestItem? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM transfer_manifest WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursorToManifestItem(cursor) else null
        }
    }

    suspend fun getPendingManifestItems(deviceId: String): List<TransferManifestItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferManifestItem>()
        val pendingStates = arrayOf(TransferStatus.QUEUED.name, TransferStatus.PAUSED_BACKPRESSURE.name, TransferStatus.TRANSFERRING.name)
        val placeholders = pendingStates.joinToString(",") { "?" }
        val args = arrayOf(deviceId) + pendingStates
        readableDatabase.rawQuery("SELECT * FROM transfer_manifest WHERE device_id = ? AND status IN ($placeholders) ORDER BY size_bytes ASC", args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToManifestItem(cursor))
            }
        }
        list
    }

    suspend fun getAllManifestItems(deviceId: String): List<TransferManifestItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferManifestItem>()
        readableDatabase.rawQuery("SELECT * FROM transfer_manifest WHERE device_id = ? ORDER BY updated_at DESC", arrayOf(deviceId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToManifestItem(cursor))
            }
        }
        list
    }

    private fun cursorToManifestItem(c: Cursor): TransferManifestItem {
        return TransferManifestItem(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            deviceId = c.getString(c.getColumnIndexOrThrow("device_id")),
            sourceUri = c.getString(c.getColumnIndexOrThrow("source_uri")),
            filename = c.getString(c.getColumnIndexOrThrow("filename")),
            mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
            sizeBytes = c.getLong(c.getColumnIndexOrThrow("size_bytes")),
            bytesTransferred = c.getLong(c.getColumnIndexOrThrow("bytes_transferred")),
            sha256Hash = c.getString(c.getColumnIndexOrThrow("sha256_hash")),
            status = TransferStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
            retryCount = c.getInt(c.getColumnIndexOrThrow("retry_count")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }
}
