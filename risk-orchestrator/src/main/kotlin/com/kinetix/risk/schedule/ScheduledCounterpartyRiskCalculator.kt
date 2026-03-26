package com.kinetix.risk.schedule

import com.kinetix.risk.service.CounterpartyRiskOrchestrationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.LocalTime
import kotlin.coroutines.coroutineContext

/**
 * Runs a post-EOD batch that computes and persists PFE/CVA for all active counterparties.
 *
 * The batch runs once per day after [eodTime].  It uses the existing
 * [CounterpartyRiskOrchestrationService.computeAndPersistPFE] with an empty position list —
 * the orchestration service fetches live positions from position-service internally.
 *
 * Per-counterparty failures are logged as errors and do not abort the remaining batch.
 */
class ScheduledCounterpartyRiskCalculator(
    private val service: CounterpartyRiskOrchestrationService,
    private val counterpartyIds: suspend () -> List<String>,
    private val eodTime: LocalTime = LocalTime.of(18, 0),
    private val intervalMillis: Long = 60_000,
    private val nowProvider: () -> LocalTime = { LocalTime.now() },
    private val lock: DistributedLock = NoOpDistributedLock(),
) {
    private val logger = LoggerFactory.getLogger(ScheduledCounterpartyRiskCalculator::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            lock.withLock("scheduled-counterparty-risk-calculator", ttlSeconds = intervalMillis / 1000) {
                try {
                    tick()
                } catch (e: Exception) {
                    logger.error("Counterparty risk batch failed unexpectedly", e)
                }
            }
            delay(intervalMillis)
        }
    }

    /**
     * Executes the batch if [nowProvider] is at or after [eodTime].
     *
     * @return the number of counterparties successfully processed.
     */
    suspend fun tick(): Int {
        val now = nowProvider()
        if (now.isBefore(eodTime)) {
            return 0
        }

        val ids = counterpartyIds()
        logger.info("Starting post-EOD counterparty risk batch for {} counterparties", ids.size)

        var successCount = 0
        for (counterpartyId in ids) {
            try {
                service.computeAndPersistPFE(
                    counterpartyId = counterpartyId,
                    positions = emptyList(),
                    numSimulations = 0,
                    seed = 0L,
                )
                successCount++
                logger.debug("Counterparty risk computed for {}", counterpartyId)
            } catch (e: Exception) {
                logger.error(
                    "Post-EOD counterparty risk calculation failed for {}: {}",
                    counterpartyId, e.message, e,
                )
            }
        }

        logger.info(
            "Post-EOD counterparty risk batch complete: {}/{} succeeded",
            successCount, ids.size,
        )
        return successCount
    }
}
