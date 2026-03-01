package com.kinetix.schema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class TradeEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("risk-orchestrator TradeEvent preserves type and status when deserializing AMEND event from position-service") {
        val positionEvent = com.kinetix.position.kafka.TradeEvent(
            tradeId = "trade-amend-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "155.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
            type = "AMEND",
            status = "LIVE",
            originalTradeId = "orig-1",
            correlationId = "corr-amend-1",
        )
        val serialized = Json.encodeToString(com.kinetix.position.kafka.TradeEvent.serializer(), positionEvent)

        val riskEvent = json.decodeFromString<com.kinetix.risk.kafka.TradeEvent>(serialized)
        riskEvent.tradeId shouldBe "trade-amend-1"
        riskEvent.portfolioId shouldBe "port-1"
        riskEvent.type shouldBe "AMEND"
        riskEvent.status shouldBe "LIVE"
        riskEvent.originalTradeId shouldBe "orig-1"
        riskEvent.correlationId shouldBe "corr-amend-1"
    }

    test("risk-orchestrator TradeEvent preserves type=CANCEL and status=CANCELLED") {
        val positionEvent = com.kinetix.position.kafka.TradeEvent(
            tradeId = "trade-cancel-1",
            portfolioId = "port-2",
            instrumentId = "MSFT",
            assetClass = "EQUITY",
            side = "SELL",
            quantity = "50",
            priceAmount = "300.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T11:00:00Z",
            type = "CANCEL",
            status = "CANCELLED",
            originalTradeId = "orig-cancel-1",
        )
        val serialized = Json.encodeToString(com.kinetix.position.kafka.TradeEvent.serializer(), positionEvent)

        val riskEvent = json.decodeFromString<com.kinetix.risk.kafka.TradeEvent>(serialized)
        riskEvent.tradeId shouldBe "trade-cancel-1"
        riskEvent.type shouldBe "CANCEL"
        riskEvent.status shouldBe "CANCELLED"
        riskEvent.originalTradeId shouldBe "orig-cancel-1"
    }

    test("audit-service TradeEvent preserves type, status, and originalTradeId from position-service") {
        val positionEvent = com.kinetix.position.kafka.TradeEvent(
            tradeId = "trade-audit-1",
            portfolioId = "port-3",
            instrumentId = "GOOG",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "10",
            priceAmount = "180.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T12:00:00Z",
            type = "AMEND",
            status = "LIVE",
            originalTradeId = "orig-audit-1",
            correlationId = "corr-audit-1",
        )
        val serialized = Json.encodeToString(com.kinetix.position.kafka.TradeEvent.serializer(), positionEvent)

        val auditEvent = json.decodeFromString<com.kinetix.audit.kafka.TradeEvent>(serialized)
        auditEvent.tradeId shouldBe "trade-audit-1"
        auditEvent.portfolioId shouldBe "port-3"
        auditEvent.type shouldBe "AMEND"
        auditEvent.status shouldBe "LIVE"
        auditEvent.originalTradeId shouldBe "orig-audit-1"
        auditEvent.correlationId shouldBe "corr-audit-1"
    }

    test("backward compatibility: risk-orchestrator defaults apply when deserializing minimal event without type or status") {
        val minimalJson = """
            {
                "tradeId": "trade-minimal-1",
                "portfolioId": "port-4",
                "instrumentId": "TSLA",
                "assetClass": "EQUITY",
                "side": "BUY",
                "quantity": "5",
                "priceAmount": "250.00",
                "priceCurrency": "USD",
                "tradedAt": "2025-01-15T13:00:00Z"
            }
        """.trimIndent()

        val riskEvent = json.decodeFromString<com.kinetix.risk.kafka.TradeEvent>(minimalJson)
        riskEvent.tradeId shouldBe "trade-minimal-1"
        riskEvent.type shouldBe "NEW"
        riskEvent.status shouldBe "LIVE"
        riskEvent.originalTradeId shouldBe null
        riskEvent.correlationId shouldBe null
    }
})
