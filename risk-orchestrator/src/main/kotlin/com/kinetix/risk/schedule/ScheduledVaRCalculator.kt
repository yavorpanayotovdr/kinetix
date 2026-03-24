package com.kinetix.risk.schedule

import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.HierarchyRiskService
import com.kinetix.risk.service.VaRCalculationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

class ScheduledVaRCalculator(
    private val varCalculationService: VaRCalculationService,
    private val varCache: VaRCache,
    private val bookIds: suspend () -> List<BookId>,
    private val intervalMillis: Long = 60_000,
    private val hierarchyRiskService: HierarchyRiskService? = null,
) {
    private val logger = LoggerFactory.getLogger(ScheduledVaRCalculator::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            try {
                val portfolios = bookIds()
                for (bookId in portfolios) {
                    try {
                        val result = varCalculationService.calculateVaR(
                            VaRCalculationRequest(
                                bookId = bookId,
                                calculationType = CalculationType.PARAMETRIC,
                                confidenceLevel = ConfidenceLevel.CL_95,
                            ),
                            triggerType = TriggerType.SCHEDULED,
                            triggeredBy = "SYSTEM",
                        )
                        if (result != null) {
                            varCache.put(bookId.value, result)
                        }
                    } catch (e: Exception) {
                        logger.error("Scheduled VaR calculation failed for portfolio {}", bookId.value, e)
                    }
                }

                // After all per-book VaRs are refreshed, aggregate the firm hierarchy.
                // This ensures the hierarchy snapshot reflects the latest book-level results.
                if (hierarchyRiskService != null) {
                    try {
                        hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")
                        logger.info("Scheduled firm-level hierarchy aggregation complete")
                    } catch (e: Exception) {
                        logger.error("Scheduled firm-level hierarchy aggregation failed", e)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch portfolio list for scheduled VaR calculation", e)
            }
            delay(intervalMillis)
        }
    }
}
