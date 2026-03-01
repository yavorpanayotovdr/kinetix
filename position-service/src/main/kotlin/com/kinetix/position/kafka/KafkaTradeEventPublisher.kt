package com.kinetix.position.kafka

import com.kinetix.common.kafka.events.TradeEvent
import com.kinetix.common.model.Trade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class KafkaTradeEventPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topic: String = "trades.lifecycle",
) : TradeEventPublisher {

    private val logger = LoggerFactory.getLogger(KafkaTradeEventPublisher::class.java)

    override suspend fun publish(trade: Trade) {
        val event = TradeEvent.from(trade)
        val json = Json.encodeToString(event)
        val record = ProducerRecord(topic, trade.portfolioId.value, json)

        try {
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to publish trade event to Kafka: tradeId={}, topic={}",
                trade.tradeId.value, topic, e,
            )
        }
    }
}
