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
) {
    private val logger = LoggerFactory.getLogger(RetryableConsumer::class.java)
    private val dlqTopic = "$topic.dlq"

    suspend fun <T> process(key: String, value: String, handler: suspend () -> T): T {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                return handler()
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
            dlqProducer.send(ProducerRecord(dlqTopic, key, value))
        } else {
            logger.error(
                "Max retries exhausted for topic={}, key={}. No DLQ producer configured.",
                topic, key,
            )
        }

        throw lastException!!
    }
}
