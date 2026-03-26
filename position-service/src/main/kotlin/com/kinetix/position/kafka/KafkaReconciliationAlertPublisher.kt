package com.kinetix.position.kafka

import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.position.fix.PrimeBrokerReconciliation
import com.kinetix.position.fix.ReconciliationBreakSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Publishes a RECONCILIATION_BREAK risk result event to the `risk.results` topic
 * when a prime broker reconciliation finds critical breaks (notional > $10,000).
 *
 * The notification-service RiskResultConsumer evaluates this event against alert
 * rules — alert rules configured for RECONCILIATION_BREAK type will fire.
 */
class KafkaReconciliationAlertPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "risk.results",
) : ReconciliationAlertPublisher {

    private val logger = LoggerFactory.getLogger(KafkaReconciliationAlertPublisher::class.java)

    override suspend fun publishBreakAlert(reconciliation: PrimeBrokerReconciliation) {
        val criticalBreaks = reconciliation.breaks.filter { it.severity == ReconciliationBreakSeverity.CRITICAL }
        if (criticalBreaks.isEmpty()) return

        val event = RiskResultEvent(
            bookId = reconciliation.bookId,
            varValue = "0.0",
            expectedShortfall = "0.0",
            calculationType = "RECONCILIATION_BREAK",
            calculatedAt = Instant.now().toString(),
        )

        val json = Json.encodeToString(event)
        val record = ProducerRecord(topic, reconciliation.bookId, json)

        try {
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
            logger.warn(
                "Published RECONCILIATION_BREAK alert for book={} criticalBreaks={}",
                reconciliation.bookId, criticalBreaks.size,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to publish RECONCILIATION_BREAK alert for book={}: {}",
                reconciliation.bookId, e.message,
            )
        }
    }
}
