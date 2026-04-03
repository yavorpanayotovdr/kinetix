package com.kinetix.audit.dlq

import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.common.kafka.events.TradeEventMessage
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Replays messages from the audit DLQ back through the audit event pipeline.
 *
 * Messages replayed via this service are marked with details="DLQ_REPLAY" so they
 * can be distinguished from real-time entries in the audit trail.
 *
 * The [messageSource] lambda is injected so the actual Kafka read strategy can be
 * swapped for test doubles without a running broker.
 */
class DlqReplayService(
    private val repository: AuditEventRepository,
    private val messageSource: suspend () -> List<DlqMessage>,
) {
    private val logger = LoggerFactory.getLogger(DlqReplayService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun replay(): DlqReplayResult {
        val messages = messageSource()
        var successCount = 0
        var failureCount = 0

        for (message in messages) {
            try {
                val tradeEvent = json.decodeFromString<TradeEventMessage>(message.value)
                val auditEvent = tradeEvent.toAuditEvent(receivedAt = Instant.now(), replayedFrom = "DLQ_REPLAY")
                repository.save(auditEvent)
                successCount++
                logger.info(
                    "DLQ replay succeeded: key={}, tradeId={}",
                    message.key, tradeEvent.tradeId,
                )
            } catch (e: Exception) {
                failureCount++
                logger.error(
                    "DLQ replay failed for key={}: {}",
                    message.key, e.message, e,
                )
            }
        }

        logger.info(
            "DLQ replay complete: total={}, success={}, failure={}",
            messages.size, successCount, failureCount,
        )

        return DlqReplayResult(
            successCount = successCount,
            failureCount = failureCount,
            total = messages.size,
        )
    }

    private fun TradeEventMessage.toAuditEvent(receivedAt: Instant, replayedFrom: String) =
        com.kinetix.audit.model.AuditEvent(
            tradeId = tradeId,
            bookId = bookId,
            instrumentId = instrumentId,
            assetClass = assetClass,
            side = side,
            quantity = quantity,
            priceAmount = priceAmount,
            priceCurrency = priceCurrency,
            tradedAt = tradedAt,
            receivedAt = receivedAt,
            userId = userId,
            userRole = userRole,
            eventType = auditEventType,
            details = replayedFrom,
        )
}
