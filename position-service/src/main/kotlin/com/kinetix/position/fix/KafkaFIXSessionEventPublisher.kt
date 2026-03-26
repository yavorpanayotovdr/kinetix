package com.kinetix.position.fix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

/**
 * Publishes FIX session lifecycle events to the `fix.session.events` topic.
 *
 * TODO(EXEC-06): subscribe notification-service to this topic and define alert
 * rules that fire on FIX_SESSION_DISCONNECTED events.
 */
class KafkaFIXSessionEventPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "fix.session.events",
) : FIXSessionEventPublisher {

    private val logger = LoggerFactory.getLogger(KafkaFIXSessionEventPublisher::class.java)

    override suspend fun publishDisconnected(event: FIXSessionDisconnectedEvent) {
        val payload = Json.encodeToString(event)
        val record = ProducerRecord(topic, event.sessionId, payload)
        try {
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
            logger.warn(
                "FIX_SESSION_DISCONNECTED published: sessionId={} counterparty={}",
                event.sessionId, event.counterparty,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to publish FIX_SESSION_DISCONNECTED for session {}: {}",
                event.sessionId, e.message,
            )
        }
    }
}
