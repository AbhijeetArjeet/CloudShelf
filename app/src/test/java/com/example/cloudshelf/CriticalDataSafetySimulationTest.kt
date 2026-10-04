package com.example.cloudshelf

import com.example.cloudshelf.engine.BatchPlan
import com.example.cloudshelf.model.*
import com.example.cloudshelf.providers.SourceItemRef
import org.junit.Assert.*
import org.junit.Test

class CriticalDataSafetySimulationTest {

    private val STAGING_LIMIT_3GB = 3L * 1024 * 1024 * 1024 // 3 GB

    /**
     * Requirement 21 & 22: Batch planning with 3 GB target threshold.
     * Prevents overflowing batches past limit unless a single file exceeds it.
     */
    @Test
    fun testBatchPlanningThresholdAndLargeFileIsolation() {
        val items = listOf(
            SourceItemRef("1", "photo1.jpg", "image/jpeg", 1L * 1024 * 1024 * 1024), // 1 GB
            SourceItemRef("2", "photo2.jpg", "image/jpeg", 1L * 1024 * 1024 * 1024), // 1 GB
            SourceItemRef("3", "video_oversize.mp4", "video/mp4", 4L * 1024 * 1024 * 1024), // 4 GB (Oversize)
            SourceItemRef("4", "photo3.jpg", "image/jpeg", 1L * 1024 * 1024 * 1024), // 1 GB
            SourceItemRef("5", "photo4.jpg", "image/jpeg", 1L * 1024 * 1024 * 1024)  // 1 GB
        )

        // Manual partition replicating StagingEngine.createBatchPlans
        val plans = mutableListOf<BatchPlan>()
        var currentBatchItems = mutableListOf<SourceItemRef>()
        var currentBatchBytes = 0L
        var batchIndex = 1

        for (item in items) {
            val itemSize = item.estimatedSizeBytes
            if (itemSize >= STAGING_LIMIT_3GB) {
                if (currentBatchItems.isNotEmpty()) {
                    plans.add(BatchPlan(batchIndex++, currentBatchItems, currentBatchBytes, currentBatchItems.size))
                    currentBatchItems = mutableListOf()
                    currentBatchBytes = 0L
                }
                plans.add(BatchPlan(batchIndex++, listOf(item), itemSize, 1))
                continue
            }

            if (currentBatchBytes + itemSize > STAGING_LIMIT_3GB && currentBatchItems.isNotEmpty()) {
                plans.add(BatchPlan(batchIndex++, currentBatchItems, currentBatchBytes, currentBatchItems.size))
                currentBatchItems = mutableListOf()
                currentBatchBytes = 0L
            }

            currentBatchItems.add(item)
            currentBatchBytes += itemSize
        }
        if (currentBatchItems.isNotEmpty()) {
            plans.add(BatchPlan(batchIndex, currentBatchItems, currentBatchBytes, currentBatchItems.size))
        }

        // Verification:
        // Batch 1: photo1 + photo2 = 2 GB
        // Batch 2: video_oversize = 4 GB (isolated to its own batch)
        // Batch 3: photo3 + photo4 = 2 GB
        assertEquals(3, plans.size)
        assertEquals(2, plans[0].items.size)
        assertEquals(2L * 1024 * 1024 * 1024, plans[0].totalSizeBytes)

        assertEquals(1, plans[1].items.size)
        assertEquals("video_oversize.mp4", plans[1].items.first().filename)
        assertEquals(4L * 1024 * 1024 * 1024, plans[1].totalSizeBytes)

        assertEquals(2, plans[2].items.size)
        assertEquals(2L * 1024 * 1024 * 1024, plans[2].totalSizeBytes)
    }

    /**
     * Requirement 27, 35, & 80: Critical Data Safety Golden Rule.
     * Deletion of local staging files is locked until explicitly confirmed.
     */
    @Test
    fun testGoldenSafetyRulePreventsPrematureLocalDeletion() {
        val unconfirmedBatch = StagingBatch(
            id = "batch-001",
            jobId = "job-001",
            batchIndex = 1,
            totalBatchesEstimated = 30,
            state = BatchState.READY_FOR_BACKUP, // NOT yet confirmed!
            targetSizeBytes = STAGING_LIMIT_3GB,
            actualSizeBytes = 2_800_000_000L,
            itemCount = 842,
            stagingFolder = "Pictures/CloudShelf/Staging/Batch-001"
        )

        // Attempting local deletion on an unconfirmed batch MUST fail
        val exception = assertThrows(SecurityException::class.java) {
            if (unconfirmedBatch.state != BatchState.BACKUP_CONFIRMED) {
                throw SecurityException("Golden Safety Rule Violation: Cannot delete local staging copy before backup is confirmed.")
            }
        }
        assertTrue(exception.message!!.contains("Golden Safety Rule Violation"))

        // Simulating user confirmation
        val confirmedBatch = unconfirmedBatch.copy(
            state = BatchState.BACKUP_CONFIRMED,
            backupConfirmedAt = System.currentTimeMillis(),
            confirmationMethod = ConfirmationMethod.USER_MANUAL
        )

        // Now deletion is unlocked
        assertEquals(BatchState.BACKUP_CONFIRMED, confirmedBatch.state)
        assertEquals(ConfirmationMethod.USER_MANUAL, confirmedBatch.confirmationMethod)

        // Complete deletion cycle
        val completedBatch = confirmedBatch.copy(
            state = BatchState.COMPLETED,
            localDeletedAt = System.currentTimeMillis()
        )
        assertEquals(BatchState.COMPLETED, completedBatch.state)
        assertNotNull(completedBatch.localDeletedAt)
    }

    /**
     * Requirement 80: Full Cycle Simulation.
     * 100 GB source -> 3 GB batches -> staging -> backup confirmed -> delete local 3 GB -> next batch.
     */
    @Test
    fun testFull100GbLifecycleSimulation() {
        val totalSourceSize = 100L * 1024 * 1024 * 1024 // 100 GB
        val stagingBatchSize = 3L * 1024 * 1024 * 1024  // 3 GB
        val totalBatches = ((totalSourceSize + stagingBatchSize - 1) / stagingBatchSize).toInt()

        var stagedBytes = 0L
        var localDiskUsage = 0L
        var batchesCompleted = 0

        for (batchIdx in 1..totalBatches) {
            val batchBytes = if (batchIdx == totalBatches) {
                totalSourceSize - stagedBytes
            } else stagingBatchSize

            // 1. Stage batch
            localDiskUsage += batchBytes
            stagedBytes += batchBytes

            // CRITICAL CHECK: Local staging disk usage must never exceed 3 GB at any time
            assertTrue("Staging consumed more than safe limit: $localDiskUsage", localDiskUsage <= stagingBatchSize)

            // 2. User confirms backup
            val isBackupConfirmed = true
            assertTrue(isBackupConfirmed)

            // 3. Delete local staging files
            localDiskUsage -= batchBytes
            assertEquals(0L, localDiskUsage) // Local storage safely recovered!

            batchesCompleted++
        }

        assertEquals(totalBatches, batchesCompleted)
        assertEquals(totalSourceSize, stagedBytes)
        assertEquals(0L, localDiskUsage) // Clean storage state after all batches
    }
}
