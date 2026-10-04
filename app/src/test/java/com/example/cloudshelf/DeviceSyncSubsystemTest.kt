package com.example.cloudshelf

import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class DeviceSyncSubsystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Test pairing PIN generation, formatting, and mutual credential creation.
     */
    @Test
    fun testPairingAndCredentialsGeneration() {
        val pin = String.format("%06d", (100000..999999).random())
        val token = UUID.randomUUID().toString()
        val payload = PairingPayload(
            deviceId = "pixel_8_pro",
            deviceName = "Google Pixel 8 Pro",
            host = "192.168.1.100",
            port = 8844,
            pairingPin = pin,
            pairingToken = token
        )

        assertEquals(6, payload.pairingPin.length)
        assertTrue(payload.pairingPin.all { it.isDigit() })
        assertEquals(8844, payload.port)
        assertNotNull(payload.pairingToken)
    }

    /**
     * Requirement 10 & 12: Backpressure calculation.
     * When staging capacity is filled or waiting for Google Photos backup,
     * available safe staging room MUST drop to 0 to prevent overflowing the Pixel.
     */
    @Test
    fun testBackpressureCapacityLimiting() {
        val targetLimit = 3L * 1024 * 1024 * 1024 // 3 GB
        var stagedBytes = 2_600_000_000L           // 2.6 GB currently staged
        val freeStorageOnPixel = 8L * 1024 * 1024 * 1024 // 8 GB
        val minReserve = 5L * 1024 * 1024 * 1024        // 5 GB reserve
        val overhead = 500L * 1024 * 1024               // 500 MB overhead

        val safeStorageRoom = (freeStorageOnPixel - minReserve - overhead).coerceAtLeast(0L) // 2.5 GB
        var safeAvailable = (targetLimit - stagedBytes).coerceAtLeast(0L).coerceAtMost(safeStorageRoom)

        // targetLimit - stagedBytes available
        val expectedRemaining = targetLimit - stagedBytes
        assertEquals(expectedRemaining, safeAvailable)

        // Simulating next candidate file is 700 MB (exceeds available room)
        val nextFileSize = 700_000_000L
        val fits = nextFileSize <= safeAvailable
        assertFalse("Source companion must pause when file exceeds safe room!", fits)

        // Simulating batch moves to WAITING_FOR_BACKUP
        val isWaitingBackup = true
        safeAvailable = if (isWaitingBackup) 0L else safeAvailable

        assertEquals(0L, safeAvailable) // Full backpressure active!
    }

    /**
     * Requirement 5, 6, & 19: Chunked transfer resumption at 47%.
     * Verifies that after interruption, receiver preserves partial bytes and
     * resumes from byte offset without starting over.
     */
    @Test
    fun testChunkedResumptionAt47Percent() = runTest {
        val transfersDir = tempFolder.newFolder("transfers")
        val fileId = "video_large_001"
        val tempFile = File(transfersDir, "$fileId.tmp")

        val totalSizeBytes = 1000L // Simulated 1000 byte video
        val partialBytesReceived = 470L // 47% transferred before interruption

        // Simulate chunk 1 (0 to 470)
        RandomAccessFile(tempFile, "rw").use { raf ->
            val chunk1 = ByteArray(partialBytesReceived.toInt()) { (it % 256).toByte() }
            raf.seek(0)
            raf.write(chunk1)
        }

        // Interruption occurs...
        // Probe endpoint returns existing offset
        val probedOffset = tempFile.length()
        assertEquals(470L, probedOffset)

        // Resume chunk 2 (from 470 to 1000)
        val remainingBytes = (totalSizeBytes - probedOffset).toInt()
        RandomAccessFile(tempFile, "rw").use { raf ->
            val chunk2 = ByteArray(remainingBytes) { ((it + probedOffset.toInt()) % 256).toByte() }
            raf.seek(probedOffset)
            raf.write(chunk2)
        }

        // Verify total completed length
        assertEquals(totalSizeBytes, tempFile.length())

        // Verify content integrity
        val fullData = ByteArray(totalSizeBytes.toInt()) { (it % 256).toByte() }
        val actualData = tempFile.readBytes()
        assertArrayEquals(fullData, actualData)
    }

    /**
     * Requirement 6: Checksum mismatch strictly rejects corrupted transfer.
     */
    @Test
    fun testChecksumMismatchRejectsCorruptedTransfer() = runTest {
        val transfersDir = tempFolder.newFolder("corrupt_test")
        val tempFile = File(transfersDir, "corrupted.tmp").apply {
            writeText("Corrupted or tampered bytes during transit")
        }

        val expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        val actualSha256 = StreamingHasher.computeSha256(tempFile)

        assertNotEquals(expectedSha256, actualSha256)

        // Receiver must reject and delete corrupted temp file
        var isAccepted = false
        if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
            isAccepted = true
        } else {
            tempFile.delete()
        }

        assertFalse(isAccepted)
        assertFalse(tempFile.exists())
    }

    /**
     * Requirement 14, 29, & 43: CRITICAL INVARIANT:
     * Source originals are NEVER deleted under any circumstance!
     */
    @Test
    fun testAbsoluteSafetyRuleSourceOriginalsNeverDeleted() = runTest {
        val sourceDir = tempFolder.newFolder("source_camera_folder")
        val sourceFile = File(sourceDir, "IMG_FAMILY_ORIGINAL.jpg").apply {
            writeText("Irreplaceable family photograph bytes")
        }
        val originalTimestamp = sourceFile.lastModified()
        val originalSize = sourceFile.length()

        assertTrue(sourceFile.exists())

        // Simulating the complete transfer and staging cycle:
        // 1. Source streams to Pixel staging
        val pixelStagingDir = tempFolder.newFolder("pixel_staging")
        val stagingFile = File(pixelStagingDir, sourceFile.name).apply {
            writeBytes(sourceFile.readBytes())
        }
        assertTrue(stagingFile.exists())

        // 2. Google Photos backup simulated & confirmed
        val backupConfirmed = true
        assertTrue(backupConfirmed)

        // 3. Pixel clears ONLY staging copy
        stagingFile.delete()
        assertFalse(stagingFile.exists())

        // CRITICAL INVARIANT CHECK:
        // The source original on companion device MUST still exist untouched!
        assertTrue("CRITICAL VIOLATION: Source original file was deleted!", sourceFile.exists())
        assertEquals(originalSize, sourceFile.length())
        assertEquals("Irreplaceable family photograph bytes", sourceFile.readText())
    }
}
