package com.kinetix.risk.kafka

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

class KafkaRiskResultPublisherIntegrationTest : FunSpec({

    test("publishes VaR result to Kafka topic and can be consumed") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "risk.results.test-1"
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaRiskResultPublisher(producer, topic)

        val result = ValuationResult(
            portfolioId = PortfolioId("port-1"),
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
            varValue = 25000.0,
            expectedShortfall = 31000.0,
            componentBreakdown = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 15000.0, 60.0),
                ComponentBreakdown(AssetClass.FIXED_INCOME, 10000.0, 40.0),
            ),
            greeks = null,
            calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
            computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
        )

        publisher.publish(result)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "publisher-test-1")
        consumer.subscribe(listOf(topic))

        val records = consumer.poll(Duration.ofSeconds(10))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe "port-1"

        val event = Json.decodeFromString<RiskResultEvent>(record.value())
        event.portfolioId shouldBe "port-1"
        event.calculationType shouldBe "PARAMETRIC"
        event.confidenceLevel shouldBe "CL_95"
        event.varValue shouldBe "25000.0"
        event.expectedShortfall shouldBe "31000.0"
        event.componentBreakdown.size shouldBe 2
        event.componentBreakdown[0].assetClass shouldBe "EQUITY"
        event.componentBreakdown[0].varContribution shouldBe "15000.0"
        event.componentBreakdown[1].assetClass shouldBe "FIXED_INCOME"

        consumer.close()
        producer.close()
    }

    test("uses portfolio ID as partition key") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "risk.results.test-2"
        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        val publisher = KafkaRiskResultPublisher(producer, topic)

        val result = ValuationResult(
            portfolioId = PortfolioId("my-portfolio"),
            calculationType = CalculationType.MONTE_CARLO,
            confidenceLevel = ConfidenceLevel.CL_99,
            varValue = 50000.0,
            expectedShortfall = 62000.0,
            componentBreakdown = emptyList(),
            greeks = null,
            calculatedAt = Instant.now(),
            computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
        )

        publisher.publish(result)

        val consumer = KafkaTestSetup.createConsumer(bootstrapServers, "publisher-test-2")
        consumer.subscribe(listOf(topic))
        val records = consumer.poll(Duration.ofSeconds(10))
        records.first().key() shouldBe "my-portfolio"

        consumer.close()
        producer.close()
    }
})
