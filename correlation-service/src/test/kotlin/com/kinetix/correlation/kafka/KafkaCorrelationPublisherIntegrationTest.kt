package com.kinetix.correlation.kafka

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

class KafkaCorrelationPublisherIntegrationTest : FunSpec({

    val bootstrapServers = KafkaTestSetup.start()

    test("publishes correlation matrix event and consumer receives it") {
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaCorrelationPublisher(producer)

        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.65, 0.65, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )
        publisher.publish(matrix)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "corr-matrix-test-group")
        consumer.subscribe(listOf("correlation.matrices"))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe "AAPL,MSFT"

        val event = Json.decodeFromString<CorrelationMatrixEvent>(record.value())
        event.labels shouldBe listOf("AAPL", "MSFT")

        consumer.close()
        producer.close()
    }
})
