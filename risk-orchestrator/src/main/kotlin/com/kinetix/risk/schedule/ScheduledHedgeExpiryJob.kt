package com.kinetix.risk.schedule

import com.kinetix.risk.persistence.HedgeRecommendationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

class ScheduledHedgeExpiryJob(
    private val repository: HedgeRecommendationRepository,
    private val intervalMillis: Long = 60_000,
    private val lock: DistributedLock = NoOpDistributedLock(),
) {
    private val logger = LoggerFactory.getLogger(ScheduledHedgeExpiryJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            lock.withLock("scheduled-hedge-expiry", ttlSeconds = intervalMillis / 1000) {
            try {
                tick()
            } catch (e: Exception) {
                logger.error("Unhandled error in hedge expiry scheduler", e)
            }
            } // end lock
            delay(intervalMillis)
        }
    }

    suspend fun tick() {
        try {
            val expired = repository.expirePending()
            if (expired > 0) {
                logger.info("Hedge expiry job marked {} pending recommendation(s) as EXPIRED", expired)
            }
        } catch (e: Exception) {
            logger.error("Hedge expiry job failed", e)
        }
    }
}
