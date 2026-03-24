package com.kinetix.risk.kafka

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.common.kafka.events.PriceEvent
import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.PnlTrigger
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.IntradayPnlService
import com.kinetix.risk.service.VaRCalculationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import kotlin.coroutines.coroutineContext

class PriceEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val varCalculationService: VaRCalculationService,
    private val affectedPortfolios: suspend () -> List<BookId>,
    private val varCache: VaRCache? = null,
    private val intradayPnlService: IntradayPnlService? = null,
    private val topic: String = "price.updates",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
) {
    private val logger = LoggerFactory.getLogger(PriceEventConsumer::class.java)

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        try {
            while (coroutineContext.isActive) {
                val records = withContext(Dispatchers.IO) {
                    consumer.poll(Duration.ofMillis(100))
                }
                if (records.isEmpty) continue

                val firstRecord = records.first()
                val priceCorrelationId = try {
                    Json.decodeFromString<PriceEvent>(firstRecord.value()).correlationId
                } catch (_: Exception) { null }

                val bookIds = try {
                    affectedPortfolios()
                } catch (e: Exception) {
                    logger.error("Failed to fetch affected portfolios", e)
                    continue
                }

                for (bookId in bookIds) {
                    try {
                        retryableConsumer.process(bookId.value, "") {
                            MDC.put("correlationId", priceCorrelationId ?: "")
                            try {
                                logger.info("Price update received, triggering VaR recalculation for portfolio {}", bookId.value)
                                val result = varCalculationService.calculateVaR(
                                    VaRCalculationRequest(
                                        bookId = bookId,
                                        calculationType = CalculationType.PARAMETRIC,
                                        confidenceLevel = ConfidenceLevel.CL_95,
                                    ),
                                    triggerType = TriggerType.PRICE_EVENT,
                                    triggeredBy = "SYSTEM",
                                )
                                if (result != null) {
                                    varCache?.put(bookId.value, result)
                                }
                                intradayPnlService?.recompute(
                                    bookId = bookId,
                                    trigger = PnlTrigger.POSITION_CHANGE,
                                    correlationId = priceCorrelationId,
                                )
                            } finally {
                                MDC.remove("correlationId")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to recalculate VaR for portfolio {} after price update and retries",
                            bookId.value, e,
                        )
                    }
                }
                withContext(Dispatchers.IO) { consumer.commitSync() }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                logger.info("Closing price event Kafka consumer")
                consumer.close(Duration.ofSeconds(10))
            }
        }
    }
}
