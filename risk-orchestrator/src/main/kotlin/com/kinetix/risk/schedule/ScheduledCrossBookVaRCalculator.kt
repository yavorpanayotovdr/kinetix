package com.kinetix.risk.schedule

import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.CrossBookVaRCache
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.CrossBookVaRRequest
import com.kinetix.risk.service.CrossBookVaRCalculationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

/**
 * Periodically computes cross-book VaR for configured portfolio groups.
 *
 * Each group is defined by a group ID and a list of constituent book IDs.
 * Groups are resolved dynamically via the [groups] supplier so they can
 * be sourced from configuration, a database table, or the reference-data
 * service hierarchy.
 */
class ScheduledCrossBookVaRCalculator(
    private val crossBookVaRService: CrossBookVaRCalculationService,
    private val crossBookVaRCache: CrossBookVaRCache,
    private val groups: suspend () -> Map<String, List<BookId>>,
    private val intervalMillis: Long = 120_000,
) {
    private val logger = LoggerFactory.getLogger(ScheduledCrossBookVaRCalculator::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            try {
                val groupMap = groups()
                for ((groupId, bookIds) in groupMap) {
                    if (bookIds.isEmpty()) continue
                    try {
                        val result = crossBookVaRService.calculate(
                            CrossBookVaRRequest(
                                bookIds = bookIds,
                                portfolioGroupId = groupId,
                                calculationType = CalculationType.PARAMETRIC,
                                confidenceLevel = ConfidenceLevel.CL_95,
                            ),
                        )
                        if (result != null) {
                            crossBookVaRCache.put(groupId, result)
                            logger.info(
                                "Scheduled cross-book VaR for group {}: VaR={}, benefit={}",
                                groupId, "%.2f".format(result.varValue), "%.2f".format(result.diversificationBenefit),
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Scheduled cross-book VaR failed for group {}", groupId, e)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch group definitions for scheduled cross-book VaR", e)
            }
            delay(intervalMillis)
        }
    }
}
