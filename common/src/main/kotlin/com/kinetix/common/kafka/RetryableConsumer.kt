package com.kinetix.common.kafka

import kotlinx.coroutines.delay
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class RetryableConsumer(
    private val topic: String,
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000,
    private val dlqProducer: KafkaProducer<String, String>? = null,
    private val livenessTracker: ConsumerLivenessTracker? = null,
) {
    private val logger = LoggerFactory.getLogger(RetryableConsumer::class.java)
    private val dlqTopic = "$topic.dlq"

    suspend fun <T> process(key: String, value: String, handler: suspend () -> T): T {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                val result = handler()
                livenessTracker?.recordSuccess()
                return result
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = baseDelayMs * (1L shl attempt)
                    logger.warn(
                        "Retry {}/{} for topic={}, key={}, delaying {}ms",
                        attempt + 1, maxRetries, topic, key, delayMs,
                    )
                    delay(delayMs)
                }
            }
        }

        if (dlqProducer != null) {
            logger.error(
                "Max retries exhausted for topic={}, key={}. Sending to DLQ topic={}",
                topic, key, dlqTopic,
            )
            try {
                dlqProducer.send(ProducerRecord(dlqTopic, key, value))
                livenessTracker?.recordDlqSend()
            } catch (dlqException: Exception) {
                logger.error(
                    "DLQ send failed for topic={}, key={}, value={}. Message cannot be recovered via DLQ.",
                    topic, key, value, dlqException,
                )
            }
        } else {
            logger.error(
                "Max retries exhausted for topic={}, key={}. No DLQ producer configured.",
                topic, key,
            )
        }

        livenessTracker?.recordError()
        throw lastException!!
    }
}
