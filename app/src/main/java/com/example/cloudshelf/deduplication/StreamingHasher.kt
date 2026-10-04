package com.example.cloudshelf.deduplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object StreamingHasher {
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer

    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        FileInputStream(file).use { computeSha256(it) }
    }

    suspend fun computeSha256(inputStream: InputStream): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hashBytes = digest.digest()
        buildString(hashBytes.size * 2) {
            for (b in hashBytes) {
                append(String.format("%02x", b))
            }
        }
    }
}
