package com.kinetix.notification.kafka

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.common.kafka.events.MarketRegimeEvent
import com.kinetix.notification.delivery.DeliveryService
import com.kinetix.notification.engine.RegimeChangeRule
import com.kinetix.notification.persistence.AlertEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.coroutineContext

class MarketRegimeEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val rule: RegimeChangeRule = RegimeChangeRule(),
    private val deliveryService: DeliveryService? = null,
    private val eventRepository: AlertEventRepository? = null,
    private val topic: String = "risk.regime.changes",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
) {
    private val logger = LoggerFactory.getLogger(MarketRegimeEventConsumer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        logger.info("Subscribed to topic: {}", topic)
        try {
            while (coroutineContext.isActive) {
                val records = withContext(Dispatchers.IO) {
                    consumer.poll(Duration.ofMillis(100))
                }
                for (record in records) {
                    try {
                        retryableConsumer.process(record.key() ?: "", record.value()) {
                            val event = json.decodeFromString<MarketRegimeEvent>(record.value())
                            processEvent(event)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to process regime event after retries: {}", e.message)
                    }
                }
                if (!records.isEmpty) {
                    withContext(Dispatchers.IO) { consumer.commitSync() }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                logger.info("Closing regime event Kafka consumer")
                consumer.close(Duration.ofSeconds(10))
            }
        }
    }

    private suspend fun processEvent(event: MarketRegimeEvent) {
        val alert = rule.evaluate(event) ?: run {
            logger.debug("Regime change to {} produced no alert (NORMAL)", event.regime)
            return
        }

        logger.info(
            "Regime change alert: from={} to={} severity={}",
            event.previousRegime, event.regime, alert.severity,
        )

        eventRepository?.save(alert)
        deliveryService?.deliver(alert)
    }
}
