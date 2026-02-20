package com.kinetix.marketdata.kafka

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun point(
    instrumentId: String = "AAPL",
    priceAmount: BigDecimal = BigDecimal("150.00"),
    currency: Currency = USD,
    timestamp: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    source: MarketDataSource = MarketDataSource.EXCHANGE,
) = MarketDataPoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(priceAmount, currency),
    timestamp = timestamp,
    source = source,
)

class KafkaMarketDataPublisherIntegrationTest : FunSpec({

    val bootstrapServers = KafkaTestSetup.start()

    test("publishes market data event to Kafka and consumer receives it") {
        val topic = "market.data.prices.roundtrip-test"
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaMarketDataPublisher(producer, topic)

        publisher.publish(point())

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "roundtrip-test-group")
        consumer.subscribe(listOf(topic))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe "AAPL"

        val event = Json.decodeFromString<MarketDataEvent>(record.value())
        event.instrumentId shouldBe "AAPL"
        event.priceAmount shouldBe "150.00"
        event.priceCurrency shouldBe "USD"
        event.timestamp shouldContain "2025-01-15T10:00:00"
        event.source shouldBe "EXCHANGE"

        consumer.close()
        producer.close()
    }

    test("uses instrumentId as partition key for ordering guarantee") {
        val topic = "market.data.prices.ordering-test"
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaMarketDataPublisher(producer, topic)

        publisher.publish(point(instrumentId = "AAPL", timestamp = Instant.parse("2025-01-15T10:00:00Z")))
        publisher.publish(point(instrumentId = "AAPL", timestamp = Instant.parse("2025-01-15T10:01:00Z")))

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "ordering-test-group")
        consumer.subscribe(listOf(topic))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 2

        val partitions = records.map { it.partition() }.toSet()
        partitions.size shouldBe 1

        val events = records.map { Json.decodeFromString<MarketDataEvent>(it.value()) }
        events[0].timestamp shouldContain "2025-01-15T10:00:00"
        events[1].timestamp shouldContain "2025-01-15T10:01:00"

        consumer.close()
        producer.close()
    }
})
