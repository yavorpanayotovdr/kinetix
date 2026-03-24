package com.kinetix.risk.kafka

import com.kinetix.common.kafka.events.TradeEventMessage
import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.model.PnlTrigger
import com.kinetix.risk.model.ValuationResult
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.IntradayPnlService
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

    test("consumes trade event and triggers VaR calculation for the portfolio and caches result") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "trades.lifecycle.test-1"
        val varService = mockk<VaRCalculationService>()
        val varCache = mockk<VaRCache>(relaxed = true)
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "trade-consumer-test-1")
        val consumer = TradeEventConsumer(kafkaConsumer, varService, varCache = varCache, topic = topic)

        val mockResult = mockk<ValuationResult>()
        var capturedBookId: String? = null
        coEvery { varService.calculateVaR(any(), any()) } answers {
            capturedBookId = firstArg<VaRCalculationRequest>().bookId.value
            mockResult
        }

        val job = launch { consumer.start() }

        val event = TradeEventMessage(
            tradeId = "trade-1",
            bookId = "port-1",
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
            while (capturedBookId == null) {
                delay(100)
            }
        }

        capturedBookId shouldBe "port-1"
        coVerify(exactly = 1) { varService.calculateVaR(match { it.bookId == BookId("port-1") }, any()) }
        coVerify(exactly = 1) { varCache.put("port-1", mockResult) }

        job.cancel()
        producer.close()
    }

    test("processes multiple trade events for different portfolios") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "trades.lifecycle.test-2"
        val varService = mockk<VaRCalculationService>()
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "trade-consumer-test-2")
        val consumer = TradeEventConsumer(kafkaConsumer, varService, topic = topic)

        val portfoliosCalculated = mutableListOf<String>()
        coEvery { varService.calculateVaR(any(), any()) } answers {
            portfoliosCalculated.add(firstArg<VaRCalculationRequest>().bookId.value)
            null
        }

        val job = launch { consumer.start() }

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        for (portId in listOf("port-A", "port-B")) {
            val event = TradeEventMessage(
                tradeId = "trade-$portId",
                bookId = portId,
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

    test("triggers intradayPnlService recompute after VaR calculation on trade event") {
        val bootstrapServers = KafkaTestSetup.start()
        val topic = "trades.lifecycle.test-3"
        val varService = mockk<VaRCalculationService>()
        val intradayPnlService = mockk<IntradayPnlService>(relaxed = true)
        val kafkaConsumer = KafkaTestSetup.createConsumer(bootstrapServers, "trade-consumer-test-3")
        val consumer = TradeEventConsumer(
            kafkaConsumer,
            varService,
            intradayPnlService = intradayPnlService,
            topic = topic,
        )

        val mockResult = mockk<ValuationResult>()
        var varCalculated = false
        coEvery { varService.calculateVaR(any(), any()) } answers {
            varCalculated = true
            mockResult
        }

        val job = launch { consumer.start() }

        val event = TradeEventMessage(
            tradeId = "trade-pnl",
            bookId = "port-pnl",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
        )

        val producer = KafkaTestSetup.createProducer(bootstrapServers)
        producer.send(ProducerRecord(topic, "port-pnl", Json.encodeToString(event))).get()

        withTimeout(10_000) {
            while (!varCalculated) {
                delay(100)
            }
        }
        delay(200) // allow P&L call to propagate

        coVerify(exactly = 1) {
            intradayPnlService.recompute(
                bookId = BookId("port-pnl"),
                trigger = PnlTrigger.TRADE_BOOKED,
                correlationId = null,
            )
        }

        job.cancel()
        producer.close()
    }
})
