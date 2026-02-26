package com.kinetix.rates.kafka

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

class KafkaRatesPublisherIntegrationTest : FunSpec({

    val bootstrapServers = KafkaTestSetup.start()

    test("publishes yield curve event and consumer receives it") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaRatesPublisher(producer)

        val curve = YieldCurve(
            curveId = "USD-TREASURY",
            currency = USD,
            asOf = NOW,
            tenors = listOf(Tenor.oneMonth(BigDecimal("0.0400")), Tenor.oneYear(BigDecimal("0.0500"))),
            source = RateSource.CENTRAL_BANK,
        )
        publisher.publishYieldCurve(curve)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "yield-curve-test-group")
        consumer.subscribe(listOf("rates.yield-curves"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe "USD-TREASURY"

        val event = Json.decodeFromString<YieldCurveEvent>(record.value())
        event.curveId shouldBe "USD-TREASURY"
        event.currency shouldBe "USD"

        consumer.close()
        producer.close()
    }

    test("publishes risk-free rate event with correct partition key") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaRatesPublisher(producer)

        val rate = RiskFreeRate(
            currency = USD,
            tenor = "3M",
            rate = 0.0525,
            asOfDate = NOW,
            source = RateSource.CENTRAL_BANK,
        )
        publisher.publishRiskFreeRate(rate)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "risk-free-rate-test-group")
        consumer.subscribe(listOf("rates.risk-free"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1
        records.first().key() shouldBe "USD:3M"

        consumer.close()
        producer.close()
    }
})
