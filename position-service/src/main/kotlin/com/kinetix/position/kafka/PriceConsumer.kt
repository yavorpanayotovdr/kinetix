package com.kinetix.position.kafka

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.position.service.PriceUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.util.*

class PriceConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val priceUpdateService: PriceUpdateService,
    private val topic: String = "price.updates",
) {
    private val logger = LoggerFactory.getLogger(PriceConsumer::class.java)

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        while (currentCoroutineContext().isActive) {
            val records = withContext(Dispatchers.IO) {
                consumer.poll(Duration.ofMillis(100))
            }
            for (record in records) {
                try {
                    val event = Json.decodeFromString<PriceEvent>(record.value())
                    val instrumentId = InstrumentId(event.instrumentId)
                    val price = Money(BigDecimal(event.priceAmount), Currency.getInstance(event.priceCurrency))
                    priceUpdateService.handle(instrumentId, price)
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process price event: offset={}, partition={}, instrumentId={}",
                        record.offset(), record.partition(), record.key(), e,
                    )
                }
            }
        }
    }
}
