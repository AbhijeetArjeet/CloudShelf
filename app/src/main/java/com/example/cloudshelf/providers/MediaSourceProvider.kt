package com.example.cloudshelf.providers

import com.example.cloudshelf.model.SourceType
import java.io.InputStream

data class SourceItemRef(
    val identifier: String,
    val filename: String,
    val mimeType: String,
    val estimatedSizeBytes: Long,
    val sidecarMetadata: SourceMetadata? = null
)

data class SourceMetadata(
    val title: String? = null,
    val description: String? = null,
    val timestampMs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class ScanSummary(
    val sourceType: SourceType,
    val sourceName: String,
    val totalItems: Int,
    val photoCount: Int,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val estimatedBatches: Int,
    val items: List<SourceItemRef>
)

interface MediaSourceProvider {
    val sourceType: SourceType
    suspend fun scanSource(): ScanSummary
    suspend fun openItemStream(itemRef: SourceItemRef): InputStream
}
