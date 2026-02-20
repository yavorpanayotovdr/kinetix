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

class TradeEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val varCalculationService: VaRCalculationService,
    private val topic: String = "trades.lifecycle",
) {
    private val logger = LoggerFactory.getLogger(TradeEventConsumer::class.java)

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        while (coroutineContext.isActive) {
            val records = withContext(Dispatchers.IO) {
                consumer.poll(Duration.ofMillis(100))
            }
            for (record in records) {
                try {
                    val event = Json.decodeFromString<TradeEvent>(record.value())
                    val portfolioId = PortfolioId(event.portfolioId)

                    logger.info("Trade event received for portfolio {}, triggering VaR recalculation", portfolioId.value)

                    varCalculationService.calculateVaR(
                        VaRCalculationRequest(
                            portfolioId = portfolioId,
                            calculationType = CalculationType.PARAMETRIC,
                            confidenceLevel = ConfidenceLevel.CL_95,
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process trade event: offset={}, partition={}",
                        record.offset(), record.partition(), e,
                    )
                }
            }
        }
    }
}
