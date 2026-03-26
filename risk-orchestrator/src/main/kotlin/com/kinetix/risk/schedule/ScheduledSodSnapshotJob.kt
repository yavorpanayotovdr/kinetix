package com.kinetix.risk.schedule

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.service.SodSnapshotService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.coroutineContext

class ScheduledSodSnapshotJob(
    private val sodSnapshotService: SodSnapshotService,
    private val bookIds: suspend () -> List<BookId>,
    private val sodTime: LocalTime = LocalTime.of(6, 0),
    private val intervalMillis: Long = 60_000,
    private val nowProvider: () -> LocalTime = { LocalTime.now() },
    private val lock: DistributedLock = NoOpDistributedLock(),
) {
    private val logger = LoggerFactory.getLogger(ScheduledSodSnapshotJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            lock.withLock("scheduled-sod-snapshot", ttlSeconds = intervalMillis / 1000) {
            try {
                tick()
            } catch (e: Exception) {
                logger.error("Failed to fetch portfolio list for scheduled SOD snapshot", e)
            }
            } // end lock
            delay(intervalMillis)
        }
    }

    suspend fun tick() {
        val now = nowProvider()
        if (now.isBefore(sodTime)) {
            return
        }

        val today = LocalDate.now()
        val portfolios = bookIds()

        for (bookId in portfolios) {
            try {
                val status = sodSnapshotService.getBaselineStatus(bookId, today)
                if (!status.exists) {
                    sodSnapshotService.createSnapshot(bookId, SnapshotType.AUTO, date = today)
                    logger.info("Auto SOD snapshot created for portfolio {}", bookId.value)
                }
            } catch (e: Exception) {
                logger.error("Scheduled SOD snapshot failed for portfolio {}", bookId.value, e)
            }
        }
    }
}
