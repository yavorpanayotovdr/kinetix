package com.kinetix.risk.schedule

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.VaRCalculationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

class ScheduledVaRCalculator(
    private val varCalculationService: VaRCalculationService,
    private val varCache: LatestVaRCache,
    private val portfolioIds: suspend () -> List<PortfolioId>,
    private val intervalMillis: Long = 60_000,
) {
    private val logger = LoggerFactory.getLogger(ScheduledVaRCalculator::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            try {
                val portfolios = portfolioIds()
                for (portfolioId in portfolios) {
                    try {
                        val result = varCalculationService.calculateVaR(
                            VaRCalculationRequest(
                                portfolioId = portfolioId,
                                calculationType = CalculationType.PARAMETRIC,
                                confidenceLevel = ConfidenceLevel.CL_95,
                            )
                        )
                        if (result != null) {
                            varCache.put(portfolioId.value, result)
                        }
                    } catch (e: Exception) {
                        logger.error("Scheduled VaR calculation failed for portfolio {}", portfolioId.value, e)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch portfolio list for scheduled VaR calculation", e)
            }
            delay(intervalMillis)
        }
    }
}
