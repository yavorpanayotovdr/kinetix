package com.kinetix.risk.kafka

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.ProducerRecord

class PriceEventConsumerIntegrationTest : FunSpec({

    test("consumes price event and triggers VaR recalculation for affected portfolios") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "price.updates.test-1"
        val varService = mockk<VaRCalculationService>()
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "price-consumer-test-1")

        val portfoliosCalculated = mutableListOf<String>()
        coEvery { varService.calculateVaR(any()) } answers {
            portfoliosCalculated.add(firstArg<VaRCalculationRequest>().portfolioId.value)
            null
        }

        val consumer = PriceEventConsumer(
            consumer = kafkaConsumer,
            varCalculationService = varService,
            affectedPortfolios = { listOf(PortfolioId("port-1"), PortfolioId("port-2")) },
            topic = topic,
        )

        val job = launch { consumer.start() }

        val event = PriceEvent(
            instrumentId = "AAPL",
            priceAmount = "175.00",
            priceCurrency = "USD",
            timestamp = "2025-01-15T10:00:00Z",
            source = "BLOOMBERG",
        )

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        producer.send(ProducerRecord(topic, "AAPL", Json.encodeToString(event))).get()

        withTimeout(10_000) {
            while (portfoliosCalculated.size < 2) {
                delay(100)
            }
        }

        portfoliosCalculated.toSet() shouldBe setOf("port-1", "port-2")

        job.cancel()
        producer.close()
    }

    test("skips recalculation when no affected portfolios") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "price.updates.test-2"
        val varService = mockk<VaRCalculationService>()
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "price-consumer-test-2")

        val consumer = PriceEventConsumer(
            consumer = kafkaConsumer,
            varCalculationService = varService,
            affectedPortfolios = { emptyList() },
            topic = topic,
        )

        val job = launch { consumer.start() }

        val event = PriceEvent(
            instrumentId = "AAPL",
            priceAmount = "175.00",
            priceCurrency = "USD",
            timestamp = "2025-01-15T10:00:00Z",
            source = "BLOOMBERG",
        )

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        producer.send(ProducerRecord(topic, "AAPL", Json.encodeToString(event))).get()

        delay(2_000)

        coVerify(exactly = 0) { varService.calculateVaR(any()) }

        job.cancel()
        producer.close()
    }
})
