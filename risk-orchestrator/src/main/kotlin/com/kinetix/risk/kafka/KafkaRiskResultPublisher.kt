package com.kinetix.risk.kafka

import com.kinetix.risk.model.ValuationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class KafkaRiskResultPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "risk.results",
) : RiskResultPublisher {

    private val logger = LoggerFactory.getLogger(KafkaRiskResultPublisher::class.java)

    override suspend fun publish(result: ValuationResult, correlationId: String?) {
        val event = RiskResultEvent.from(result, correlationId)
        val json = Json.encodeToString(event)
        val record = ProducerRecord(topic, result.portfolioId.value, json)

        try {
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to publish risk result to Kafka: portfolioId={}, topic={}",
                result.portfolioId.value, topic, e,
            )
        }
    }
}
