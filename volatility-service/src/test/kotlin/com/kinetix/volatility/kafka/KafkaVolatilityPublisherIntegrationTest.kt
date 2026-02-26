package com.kinetix.volatility.kafka

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class KafkaVolatilityPublisherIntegrationTest : FunSpec({

    val bootstrapServers = KafkaTestSetup.start()

    test("publishes vol surface event and consumer receives it") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaVolatilityPublisher(producer)

        val surface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-01-15T10:00:00Z"),
            points = listOf(VolPoint(BigDecimal("100"), 30, BigDecimal("0.25"))),
            source = VolatilitySource.BLOOMBERG,
        )
        publisher.publishSurface(surface)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "vol-surface-test-group")
        consumer.subscribe(listOf("volatility.surfaces"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe "AAPL"

        val event = Json.decodeFromString<VolSurfaceEvent>(record.value())
        event.instrumentId shouldBe "AAPL"

        consumer.close()
        producer.close()
    }
})
