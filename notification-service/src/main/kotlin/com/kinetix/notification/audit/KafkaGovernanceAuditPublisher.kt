package com.kinetix.notification.audit

import com.kinetix.common.audit.GovernanceAuditEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class KafkaGovernanceAuditPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "governance.audit",
) : GovernanceAuditPublisher {
    private val logger = LoggerFactory.getLogger(KafkaGovernanceAuditPublisher::class.java)

    override fun publish(event: GovernanceAuditEvent) {
        val key = event.eventType.name
        val json = Json.encodeToString(event)
        val record = ProducerRecord(topic, key, json)
        try {
            producer.send(record)
            logger.info(
                "Published governance audit event: type={}, bookId={}",
                event.eventType, event.bookId,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to publish governance audit event: type={}, topic={}",
                event.eventType, topic, e,
            )
        }
    }
}
