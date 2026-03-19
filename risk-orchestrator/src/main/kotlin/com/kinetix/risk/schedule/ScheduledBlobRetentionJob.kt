package com.kinetix.risk.schedule

import com.kinetix.risk.persistence.BlobRetentionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.LocalTime
import kotlin.coroutines.coroutineContext

class ScheduledBlobRetentionJob(
    private val blobRetentionRepository: BlobRetentionRepository,
    private val retentionDays: Long = 2555,
    private val runAtTime: LocalTime = LocalTime.of(3, 0),
    private val intervalMillis: Long = 60_000,
    private val nowProvider: () -> LocalTime = { LocalTime.now() },
) {
    private val logger = LoggerFactory.getLogger(ScheduledBlobRetentionJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            try {
                tick()
            } catch (e: Exception) {
                logger.error("Unhandled error in blob retention scheduler", e)
            }
            delay(intervalMillis)
        }
    }

    suspend fun tick() {
        val now = nowProvider()
        if (now.isBefore(runAtTime)) {
            return
        }

        try {
            val deleted = blobRetentionRepository.deleteOrphanedBlobs(retentionDays)
            logger.info("Blob retention cleanup deleted {} orphaned blobs older than {} days", deleted, retentionDays)
        } catch (e: Exception) {
            logger.error("Blob retention cleanup failed", e)
        }
    }
}
