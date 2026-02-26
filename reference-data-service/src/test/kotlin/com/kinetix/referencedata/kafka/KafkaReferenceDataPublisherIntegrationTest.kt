package com.kinetix.referencedata.kafka

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class KafkaReferenceDataPublisherIntegrationTest : FunSpec({

    val bootstrapServers = KafkaTestSetup.start()

    test("publishes dividend yield event and consumer receives it") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaReferenceDataPublisher(producer)

        val yield = DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0065,
            exDate = LocalDate.of(2026, 3, 15),
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            source = ReferenceDataSource.BLOOMBERG,
        )
        publisher.publishDividendYield(yield)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "dividend-yield-test-group")
        consumer.subscribe(listOf("reference-data.dividends"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1
        records.first().key() shouldBe "AAPL"

        val event = Json.decodeFromString<DividendYieldEvent>(records.first().value())
        event.instrumentId shouldBe "AAPL"

        consumer.close()
        producer.close()
    }

    test("publishes credit spread event and consumer receives it") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaReferenceDataPublisher(producer)

        val spread = CreditSpread(
            instrumentId = InstrumentId("CORP-BOND-1"),
            spread = 0.0125,
            rating = "AA",
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            source = ReferenceDataSource.RATING_AGENCY,
        )
        publisher.publishCreditSpread(spread)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "credit-spread-test-group")
        consumer.subscribe(listOf("reference-data.credit-spreads"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1
        records.first().key() shouldBe "CORP-BOND-1"

        consumer.close()
        producer.close()
    }
})
