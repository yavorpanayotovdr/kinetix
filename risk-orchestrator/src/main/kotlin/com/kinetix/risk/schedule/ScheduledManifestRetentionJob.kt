package com.kinetix.risk.schedule

import com.kinetix.risk.persistence.ManifestRetentionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.LocalTime
import kotlin.coroutines.coroutineContext

class ScheduledManifestRetentionJob(
    private val manifestRetentionRepository: ManifestRetentionRepository,
    private val retentionDays: Long = 2555,
    private val runAtTime: LocalTime = LocalTime.of(3, 30),
    private val intervalMillis: Long = 60_000,
    private val nowProvider: () -> LocalTime = { LocalTime.now() },
    private val lock: DistributedLock = NoOpDistributedLock(),
) {
    private val logger = LoggerFactory.getLogger(ScheduledManifestRetentionJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            lock.withLock("scheduled-manifest-retention", ttlSeconds = intervalMillis / 1000) {
            try {
                tick()
            } catch (e: Exception) {
                logger.error("Unhandled error in manifest retention scheduler", e)
            }
            } // end lock
            delay(intervalMillis)
        }
    }

    suspend fun tick() {
        val now = nowProvider()
        if (now.isBefore(runAtTime)) {
            return
        }

        try {
            val deleted = manifestRetentionRepository.deleteExpiredManifests(retentionDays)
            logger.info(
                "Manifest retention cleanup deleted {} expired manifests older than {} days (including position snapshots and market data refs)",
                deleted,
                retentionDays,
            )
        } catch (e: Exception) {
            logger.error("Manifest retention cleanup failed", e)
        }
    }
}
