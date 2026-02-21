package com.kinetix.notification.kafka

import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

class AnomalyEventConsumer(
    bootstrapServers: String,
    private val topic: String = "risk.anomalies",
    groupId: String = "notification-anomaly-consumer",
) {
    private val logger = LoggerFactory.getLogger(AnomalyEventConsumer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val consumer: KafkaConsumer<String, String> = KafkaConsumer(
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        },
    )

    fun start() {
        consumer.subscribe(listOf(topic))
        logger.info("Subscribed to topic: {}", topic)
    }

    fun poll(): List<AnomalyEvent> {
        val records = consumer.poll(Duration.ofMillis(100))
        return records.mapNotNull { record ->
            try {
                val event = json.decodeFromString<AnomalyEvent>(record.value())
                if (event.isAnomaly) {
                    logger.warn(
                        "Anomaly detected: metric={}, score={}, explanation={}",
                        event.metricName, event.anomalyScore, event.explanation,
                    )
                }
                event
            } catch (e: Exception) {
                logger.error("Failed to deserialize anomaly event: {}", e.message)
                null
            }
        }
    }

    fun close() {
        consumer.close()
    }
}
