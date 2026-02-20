package com.kinetix.risk.kafka

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.VaRCalculationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.coroutineContext

class MarketDataEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val varCalculationService: VaRCalculationService,
    private val affectedPortfolios: suspend () -> List<PortfolioId>,
    private val topic: String = "market.data.prices",
) {
    private val logger = LoggerFactory.getLogger(MarketDataEventConsumer::class.java)

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        while (coroutineContext.isActive) {
            val records = withContext(Dispatchers.IO) {
                consumer.poll(Duration.ofMillis(100))
            }
            if (records.isEmpty) continue

            val portfolioIds = try {
                affectedPortfolios()
            } catch (e: Exception) {
                logger.error("Failed to fetch affected portfolios", e)
                continue
            }

            for (portfolioId in portfolioIds) {
                try {
                    logger.info("Market data update received, triggering VaR recalculation for portfolio {}", portfolioId.value)
                    varCalculationService.calculateVaR(
                        VaRCalculationRequest(
                            portfolioId = portfolioId,
                            calculationType = CalculationType.PARAMETRIC,
                            confidenceLevel = ConfidenceLevel.CL_95,
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Failed to recalculate VaR for portfolio {} after market data update",
                        portfolioId.value, e,
                    )
                }
            }
        }
    }
}
