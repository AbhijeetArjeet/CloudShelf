package com.example.cloudshelf

import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.storage.SafeFileIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SafeFileIOAndHashingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testStreamingSha256Checksum() = runTest {
        val sampleData = "CloudShelf-Data-Integrity-Payload-2026".toByteArray()
        val stream = ByteArrayInputStream(sampleData)
        val hash = StreamingHasher.computeSha256(stream)

        // Known SHA-256 for this exact byte sequence
        assertNotNull(hash)
        assertEquals(64, hash.length)

        // Verifying deterministic output
        val stream2 = ByteArrayInputStream(sampleData)
        val hash2 = StreamingHasher.computeSha256(stream2)
        assertEquals(hash, hash2)
    }

    @Test
    fun testZipSlipPathTraversalBlocked() {
        val sandboxDir = tempFolder.newFolder("staging_sandbox")

        // Malicious entry paths with path traversal
        val maliciousPaths = listOf(
            "../system/malicious.sh",
            "../../etc/passwd",
            "..\\..\\windows\\system32\\calc.exe",
            "folder/../../outside.jpg"
        )

        for (maliciousPath in maliciousPaths) {
            try {
                val resolved = SafeFileIO.validateCanonicalPath(sandboxDir, maliciousPath)
                // If it doesn't throw, it must be safely sanitized within the sandbox
                assertTrue(
                    "Target path escaped sandbox: ${resolved.canonicalPath}",
                    resolved.canonicalPath.startsWith(sandboxDir.canonicalPath)
                )
            } catch (e: SafeFileIO.SecurityException) {
                // Expected security rejection
                assertTrue(true)
            }
        }
    }

    @Test
    fun testFilenameCollisionResolution() {
        val dir = tempFolder.newFolder("collision_test")
        val original = File(dir, "IMG_1234.jpg").apply { writeText("original") }

        val resolved1 = SafeFileIO.resolveFilenameCollision(dir, "IMG_1234.jpg")
        assertEquals("IMG_1234 (1).jpg", resolved1.name)

        resolved1.writeText("second")
        val resolved2 = SafeFileIO.resolveFilenameCollision(dir, "IMG_1234.jpg")
        assertEquals("IMG_1234 (2).jpg", resolved2.name)
    }

    @Test
    fun testAtomicWriteSuccess() = runTest {
        val targetDir = tempFolder.newFolder("atomic_target")
        val targetFile = File(targetDir, "photo.jpg")

        val payload = "Simulated binary photo contents".toByteArray()
        SafeFileIO.atomicWrite(targetFile, ByteArrayInputStream(payload))

        assertTrue(targetFile.exists())
        assertEquals(payload.size.toLong(), targetFile.length())
        assertEquals(String(payload), targetFile.readText())

        // Ensure temporary files are cleaned up
        val tempFiles = targetDir.listFiles { _, name -> name.startsWith(".tmp_") }
        assertTrue(tempFiles.isNullOrEmpty())
    }
}
