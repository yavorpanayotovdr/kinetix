package com.kinetix.risk.kafka

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.common.kafka.events.TradeEventMessage
import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.PnlTrigger
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

class TradeEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val varCalculationService: VaRCalculationService,
    private val varCache: VaRCache? = null,
    private val intradayPnlService: IntradayPnlService? = null,
    private val topic: String = "trades.lifecycle",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
) {
    private val logger = LoggerFactory.getLogger(TradeEventConsumer::class.java)

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        try {
            while (coroutineContext.isActive) {
                val records = withContext(Dispatchers.IO) {
                    consumer.poll(Duration.ofMillis(100))
                }
                for (record in records) {
                    try {
                        retryableConsumer.process(record.key() ?: "", record.value()) {
                            val event = Json.decodeFromString<TradeEventMessage>(record.value())
                            MDC.put("correlationId", event.correlationId ?: "")
                            try {
                                val bookId = BookId(event.bookId)

                                logger.info("Trade event received for portfolio {}, triggering VaR recalculation", bookId.value)

                                val result = varCalculationService.calculateVaR(
                                    VaRCalculationRequest(
                                        bookId = bookId,
                                        calculationType = CalculationType.PARAMETRIC,
                                        confidenceLevel = ConfidenceLevel.CL_95,
                                    ),
                                    triggerType = TriggerType.TRADE_EVENT,
                                    correlationId = event.correlationId,
                                    triggeredBy = "SYSTEM",
                                )
                                if (result != null) {
                                    varCache?.put(bookId.value, result)
                                }
                                try {
                                    intradayPnlService?.recompute(
                                        bookId = bookId,
                                        trigger = PnlTrigger.TRADE_BOOKED,
                                        correlationId = event.correlationId,
                                    )
                                } catch (e: Exception) {
                                    logger.warn(
                                        "Intraday P&L recompute failed after trade event for book {}: {}",
                                        bookId.value, e.message,
                                    )
                                }
                            } finally {
                                MDC.remove("correlationId")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to process trade event after retries: offset={}, partition={}",
                            record.offset(), record.partition(), e,
                        )
                    }
                }
                if (!records.isEmpty) {
                    withContext(Dispatchers.IO) { consumer.commitSync() }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                logger.info("Closing trade event Kafka consumer")
                consumer.close(Duration.ofSeconds(10))
            }
        }
    }
}
