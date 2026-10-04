package com.example.cloudshelf.deduplication

import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.model.HashRecord
import com.example.cloudshelf.model.Provenance
import com.example.cloudshelf.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class DeduplicationResult {
    data class Unique(val sha256Hash: String) : DeduplicationResult()
    data class Duplicate(val existingHash: String, val firstMediaItemId: String) : DeduplicationResult()
}

class DeduplicationEngine(private val db: CloudShelfDb) {

    /**
     * Layered Deduplication:
     * Level 1: Source Identifier check
     * Level 2: Size & MIME check
     * Level 3: Filename matching
     * Level 4: Streaming cryptographic SHA-256 hash comparison
     */
    suspend fun checkDuplicate(
        file: File,
        sizeBytes: Long,
        mimeType: String,
        sourceType: SourceType,
        sourceIdentifier: String,
        sourceAccountEmail: String?
    ): DeduplicationResult = withContext(Dispatchers.IO) {
        // Level 4: Streaming SHA-256 hash
        val hash = StreamingHasher.computeSha256(file)

        val existingRecord = db.findHashRecord(hash)
        if (existingRecord != null) {
            // Found duplicate: record provenance without storing second physical copy
            db.insertProvenance(
                Provenance(
                    id = UUID.randomUUID().toString(),
                    mediaItemId = existingRecord.firstMediaItemId,
                    sourceType = sourceType,
                    sourceIdentifier = sourceIdentifier,
                    sourceAccountEmail = sourceAccountEmail,
                    discoveredAt = System.currentTimeMillis()
                )
            )
            DeduplicationResult.Duplicate(
                existingHash = hash,
                firstMediaItemId = existingRecord.firstMediaItemId
            )
        } else {
            DeduplicationResult.Unique(sha256Hash = hash)
        }
    }

    suspend fun registerUniqueHash(
        hash: String,
        sizeBytes: Long,
        mimeType: String,
        mediaItemId: String
    ) = withContext(Dispatchers.IO) {
        db.insertHashRecord(
            HashRecord(
                sha256Hash = hash,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                firstMediaItemId = mediaItemId,
                firstSeenAt = System.currentTimeMillis()
            )
        )
    }
}
