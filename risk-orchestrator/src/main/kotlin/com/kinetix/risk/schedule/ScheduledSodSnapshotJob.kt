package com.kinetix.risk.schedule

import com.kinetix.common.model.PortfolioId
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
    private val portfolioIds: suspend () -> List<PortfolioId>,
    private val sodTime: LocalTime = LocalTime.of(6, 0),
    private val intervalMillis: Long = 60_000,
    private val nowProvider: () -> LocalTime = { LocalTime.now() },
) {
    private val logger = LoggerFactory.getLogger(ScheduledSodSnapshotJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            try {
                tick()
            } catch (e: Exception) {
                logger.error("Failed to fetch portfolio list for scheduled SOD snapshot", e)
            }
            delay(intervalMillis)
        }
    }

    suspend fun tick() {
        val now = nowProvider()
        if (now.isBefore(sodTime)) {
            return
        }

        val today = LocalDate.now()
        val portfolios = portfolioIds()

        for (portfolioId in portfolios) {
            try {
                val status = sodSnapshotService.getBaselineStatus(portfolioId, today)
                if (!status.exists) {
                    sodSnapshotService.createSnapshot(portfolioId, SnapshotType.AUTO, date = today)
                    logger.info("Auto SOD snapshot created for portfolio {}", portfolioId.value)
                }
            } catch (e: Exception) {
                logger.error("Scheduled SOD snapshot failed for portfolio {}", portfolioId.value, e)
            }
        }
    }
}
