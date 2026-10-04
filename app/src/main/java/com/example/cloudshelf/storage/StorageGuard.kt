package com.example.cloudshelf.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.cloudshelf.model.StorageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageGuard(private val context: Context) {

    companion object {
        const val OVERHEAD_BUFFER_BYTES = 500L * 1024 * 1024 // 500 MB temporary buffer
    }

    suspend fun getStorageStatus(minReserveBytes: Long): StorageStatus = withContext(Dispatchers.IO) {
        val path = Environment.getExternalStorageDirectory().path
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val freeBytes = stat.availableBlocksLong * blockSize

        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val cloudshelfDir = File(picturesDir, "CloudShelf")
        val stagingDir = File(cloudshelfDir, "Staging")
        val permanentDir = File(cloudshelfDir, "Permanent")

        val stagingUsage = calculateDirectorySize(stagingDir)
        val permanentUsage = calculateDirectorySize(permanentDir)

        val safeAvailable = (freeBytes - minReserveBytes - OVERHEAD_BUFFER_BYTES).coerceAtLeast(0L)
        val isSafe = freeBytes > (minReserveBytes + OVERHEAD_BUFFER_BYTES)

        StorageStatus(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            stagingUsageBytes = stagingUsage,
            permanentUsageBytes = permanentUsage,
            minReserveBytes = minReserveBytes,
            safeAvailableBytes = safeAvailable,
            isSafeForNextBatch = isSafe
        )
    }

    suspend fun canSafelyStage(requiredBytes: Long, minReserveBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val remainingAfterBatch = freeBytes - requiredBytes - OVERHEAD_BUFFER_BYTES
        remainingAfterBatch >= minReserveBytes
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }
}
