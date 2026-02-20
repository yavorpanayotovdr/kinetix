package com.kinetix.audit.kafka

import com.kinetix.audit.persistence.AuditEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.coroutineContext

class AuditEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val repository: AuditEventRepository,
    private val topic: String = "trades.lifecycle",
) {
    private val logger = LoggerFactory.getLogger(AuditEventConsumer::class.java)

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
                    val auditEvent = event.toAuditEvent(receivedAt = Instant.now())
                    repository.save(auditEvent)
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process audit event: offset={}, partition={}",
                        record.offset(), record.partition(), e,
                    )
                }
            }
        }
    }
}
