package com.kinetix.marketdata.kafka

import com.kinetix.common.model.MarketDataPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class KafkaMarketDataPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "market.data.prices",
) : MarketDataPublisher {

    private val logger = LoggerFactory.getLogger(KafkaMarketDataPublisher::class.java)

    override suspend fun publish(point: MarketDataPoint) {
        val event = MarketDataEvent.from(point)
        val json = Json.encodeToString(event)
        val record = ProducerRecord(topic, point.instrumentId.value, json)

        try {
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to publish market data event to Kafka: instrumentId={}, topic={}",
                point.instrumentId.value, topic, e,
            )
        }
    }
}
