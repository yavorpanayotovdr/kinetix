package com.kinetix.schema

import com.kinetix.common.kafka.events.TradeEventMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class TradeEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("TradeEventMessage preserves eventType and status when serializing an AMEND event") {
        val event = TradeEventMessage(
            tradeId = "trade-amend-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "155.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
            eventType = "AMEND",
            status = "LIVE",
            originalTradeId = "orig-1",
            correlationId = "corr-amend-1",
        )
        val serialized = Json.encodeToString(TradeEventMessage.serializer(), event)

        val deserialized = json.decodeFromString<TradeEventMessage>(serialized)
        deserialized.tradeId shouldBe "trade-amend-1"
        deserialized.portfolioId shouldBe "port-1"
        deserialized.eventType shouldBe "AMEND"
        deserialized.status shouldBe "LIVE"
        deserialized.originalTradeId shouldBe "orig-1"
        deserialized.correlationId shouldBe "corr-amend-1"
    }

    test("TradeEventMessage preserves eventType=CANCEL and status=CANCELLED") {
        val event = TradeEventMessage(
            tradeId = "trade-cancel-1",
            portfolioId = "port-2",
            instrumentId = "MSFT",
            assetClass = "EQUITY",
            side = "SELL",
            quantity = "50",
            priceAmount = "300.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T11:00:00Z",
            eventType = "CANCEL",
            status = "CANCELLED",
            originalTradeId = "orig-cancel-1",
        )
        val serialized = Json.encodeToString(TradeEventMessage.serializer(), event)

        val deserialized = json.decodeFromString<TradeEventMessage>(serialized)
        deserialized.tradeId shouldBe "trade-cancel-1"
        deserialized.eventType shouldBe "CANCEL"
        deserialized.status shouldBe "CANCELLED"
        deserialized.originalTradeId shouldBe "orig-cancel-1"
    }

    test("TradeEventMessage preserves audit fields (userId, userRole, auditEventType)") {
        val event = TradeEventMessage(
            tradeId = "trade-audit-1",
            portfolioId = "port-3",
            instrumentId = "GOOG",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "10",
            priceAmount = "180.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T12:00:00Z",
            eventType = "AMEND",
            status = "LIVE",
            originalTradeId = "orig-audit-1",
            correlationId = "corr-audit-1",
            userId = "user-42",
            userRole = "TRADER",
            auditEventType = "TRADE_AMENDED",
        )
        val serialized = Json.encodeToString(TradeEventMessage.serializer(), event)

        val deserialized = json.decodeFromString<TradeEventMessage>(serialized)
        deserialized.tradeId shouldBe "trade-audit-1"
        deserialized.eventType shouldBe "AMEND"
        deserialized.status shouldBe "LIVE"
        deserialized.originalTradeId shouldBe "orig-audit-1"
        deserialized.correlationId shouldBe "corr-audit-1"
        deserialized.userId shouldBe "user-42"
        deserialized.userRole shouldBe "TRADER"
        deserialized.auditEventType shouldBe "TRADE_AMENDED"
    }

    test("backward compatibility: defaults apply when deserializing minimal event without optional fields") {
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

        val event = json.decodeFromString<TradeEventMessage>(minimalJson)
        event.tradeId shouldBe "trade-minimal-1"
        event.eventType shouldBe "NEW"
        event.status shouldBe "LIVE"
        event.originalTradeId shouldBe null
        event.correlationId shouldBe null
        event.userId shouldBe null
        event.userRole shouldBe null
        event.auditEventType shouldBe "TRADE_BOOKED"
        event.bookId shouldBe "port-4"
    }

    test("bookId is serialized alongside portfolioId in new events") {
        val event = TradeEventMessage(
            tradeId = "trade-new-1",
            portfolioId = "book-101",
            bookId = "book-101",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "10",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
        )
        val serialized = Json.encodeToString(TradeEventMessage.serializer(), event)

        val deserialized = json.decodeFromString<TradeEventMessage>(serialized)
        deserialized.portfolioId shouldBe "book-101"
        deserialized.bookId shouldBe "book-101"
    }

    test("old JSON without bookId deserializes with bookId defaulting to portfolioId") {
        val oldJson = """
            {
                "tradeId": "trade-old-1",
                "portfolioId": "port-legacy",
                "instrumentId": "MSFT",
                "assetClass": "EQUITY",
                "side": "SELL",
                "quantity": "20",
                "priceAmount": "300.00",
                "priceCurrency": "USD",
                "tradedAt": "2025-01-15T10:00:00Z"
            }
        """.trimIndent()

        val event = json.decodeFromString<TradeEventMessage>(oldJson)
        event.portfolioId shouldBe "port-legacy"
        event.bookId shouldBe "port-legacy"
    }
})
