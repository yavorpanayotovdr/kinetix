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

class TradeEventConsumerIntegrationTest : FunSpec({

    test("consumes trade event and triggers VaR calculation for the portfolio") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "trades.lifecycle.test-1"
        val varService = mockk<VaRCalculationService>()
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "trade-consumer-test-1")
        val consumer = TradeEventConsumer(kafkaConsumer, varService, topic)

        var capturedPortfolioId: String? = null
        coEvery { varService.calculateVaR(any(), any()) } answers {
            capturedPortfolioId = firstArg<VaRCalculationRequest>().portfolioId.value
            null
        }

        val job = launch { consumer.start() }

        val event = TradeEvent(
            tradeId = "trade-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
        )

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        producer.send(ProducerRecord(topic, "port-1", Json.encodeToString(event))).get()

        withTimeout(10_000) {
            while (capturedPortfolioId == null) {
                delay(100)
            }
        }

        capturedPortfolioId shouldBe "port-1"
        coVerify(exactly = 1) { varService.calculateVaR(match { it.portfolioId == PortfolioId("port-1") }, any()) }

        job.cancel()
        producer.close()
    }

    test("processes multiple trade events for different portfolios") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "trades.lifecycle.test-2"
        val varService = mockk<VaRCalculationService>()
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "trade-consumer-test-2")
        val consumer = TradeEventConsumer(kafkaConsumer, varService, topic)

        val portfoliosCalculated = mutableListOf<String>()
        coEvery { varService.calculateVaR(any(), any()) } answers {
            portfoliosCalculated.add(firstArg<VaRCalculationRequest>().portfolioId.value)
            null
        }

        val job = launch { consumer.start() }

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        for (portId in listOf("port-A", "port-B")) {
            val event = TradeEvent(
                tradeId = "trade-$portId",
                portfolioId = portId,
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "100",
                priceAmount = "150.00",
                priceCurrency = "USD",
                tradedAt = "2025-01-15T10:00:00Z",
            )
            producer.send(ProducerRecord(topic, portId, Json.encodeToString(event))).get()
        }

        withTimeout(10_000) {
            while (portfoliosCalculated.size < 2) {
                delay(100)
            }
        }

        portfoliosCalculated.toSet() shouldBe setOf("port-A", "port-B")

        job.cancel()
        producer.close()
    }
})
