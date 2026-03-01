package com.kinetix.audit.kafka

import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.common.kafka.RetryableConsumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.coroutineContext

class AuditEventConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val repository: AuditEventRepository,
    private val topic: String = "trades.lifecycle",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
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
                    retryableConsumer.process(record.key() ?: "", record.value()) {
                        val event = Json.decodeFromString<TradeEvent>(record.value())
                        MDC.put("correlationId", event.correlationId ?: "")
                        try {
                            val auditEvent = event.toAuditEvent(receivedAt = Instant.now())
                            repository.save(auditEvent)
                            logger.info("Audit event persisted: tradeId={}, portfolioId={}, eventType={}", auditEvent.tradeId, auditEvent.portfolioId, auditEvent.eventType)
                        } finally {
                            MDC.remove("correlationId")
                        }
                    }
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process audit event after retries: offset={}, partition={}",
                        record.offset(), record.partition(), e,
                    )
                }
            }
        }
    }
}
