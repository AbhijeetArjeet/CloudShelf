package com.example.cloudshelf.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object SafeFileIO {
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB

    class SecurityException(message: String) : IOException(message)

    /**
     * Zip-Slip path normalization check.
     * Prevents archive entries from escaping the staging directory.
     */
    fun validateCanonicalPath(baseDir: File, relativePath: String): File {
        // Strip leading slashes and drive letters
        val sanitizedPath = relativePath.replace('\\', '/')
            .trimStart('/')
            .replace("../", "")
            .replace("..\\", "")

        val targetFile = File(baseDir, sanitizedPath)
        val canonicalBase = baseDir.canonicalPath
        val canonicalTarget = targetFile.canonicalPath

        if (!canonicalTarget.startsWith(canonicalBase)) {
            throw SecurityException("Path traversal attempt detected in archive entry: $relativePath")
        }
        return targetFile
    }

    /**
     * Resolves filename collisions preserving original name format (e.g. IMG_1234 (1).jpg)
     */
    fun resolveFilenameCollision(targetDir: File, preferredName: String): File {
        val candidate = File(targetDir, preferredName)
        if (!candidate.exists()) return candidate

        val dotIndex = preferredName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) preferredName.substring(0, dotIndex) else preferredName
        val extension = if (dotIndex != -1) preferredName.substring(dotIndex) else ""

        var counter = 1
        while (true) {
            val numberedName = "$baseName ($counter)$extension"
            val file = File(targetDir, numberedName)
            if (!file.exists()) return file
            counter++
        }
    }

    /**
     * Atomic streaming file writer using temporary staging files.
     */
    suspend fun atomicWrite(
        targetFile: File,
        inputStream: InputStream,
        maxExpectedBytes: Long? = null
    ): File = withContext(Dispatchers.IO) {
        val parent = targetFile.parentFile ?: throw IOException("Invalid target directory")
        if (!parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent, ".tmp_${System.currentTimeMillis()}_${targetFile.name}")
        var bytesWritten = 0L

        try {
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    bytesWritten += read
                    if (maxExpectedBytes != null && bytesWritten > maxExpectedBytes * 10) {
                        throw SecurityException("Decompression bomb protection triggered. Exceeded maximum bounds.")
                    }
                }
                out.flush()
                out.fd.sync()
            }

            // Atomic rename
            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if cross-filesystem or OS lock
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }
}
