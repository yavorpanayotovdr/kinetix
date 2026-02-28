package com.kinetix.notification.kafka

import com.kinetix.common.kafka.RetryableConsumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.coroutineContext

class AnomalyEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val topic: String = "risk.anomalies",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
) {
    private val logger = LoggerFactory.getLogger(AnomalyEventConsumer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        logger.info("Subscribed to topic: {}", topic)
        while (coroutineContext.isActive) {
            val records = withContext(Dispatchers.IO) {
                consumer.poll(Duration.ofMillis(100))
            }
            for (record in records) {
                try {
                    retryableConsumer.process(record.key() ?: "", record.value()) {
                        val event = json.decodeFromString<AnomalyEvent>(record.value())
                        if (event.isAnomaly) {
                            logger.warn(
                                "Anomaly detected: metric={}, score={}, explanation={}",
                                event.metricName, event.anomalyScore, event.explanation,
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process anomaly event after retries: {}", e.message)
                }
            }
        }
    }
}
