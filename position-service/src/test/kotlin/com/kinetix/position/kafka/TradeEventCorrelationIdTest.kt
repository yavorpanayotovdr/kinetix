package com.kinetix.position.kafka

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

private fun trade(tradeId: String = "t-1") = Trade(
    tradeId = TradeId(tradeId),
    portfolioId = PortfolioId("port-1"),
    instrumentId = InstrumentId("AAPL"),
    assetClass = AssetClass.EQUITY,
    side = Side.BUY,
    quantity = BigDecimal("100"),
    price = Money(BigDecimal("150.00"), USD),
    tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class TradeEventCorrelationIdTest : FunSpec({

    test("TradeEvent.from generates correlationId when not provided") {
        val event = TradeEvent.from(trade())

        event.correlationId.shouldNotBeNull()
        event.correlationId!! shouldMatch UUID_REGEX
    }

    test("TradeEvent.from uses provided correlationId") {
        val event = TradeEvent.from(trade(), correlationId = "my-correlation-id")

        event.correlationId shouldBe "my-correlation-id"
    }

    test("correlationId survives JSON round-trip") {
        val event = TradeEvent.from(trade(), correlationId = "test-corr-123")
        val json = Json.encodeToString(event)
        val deserialized = Json.decodeFromString<TradeEvent>(json)

        deserialized.correlationId shouldBe "test-corr-123"
    }

    test("correlationId defaults to null for backward-compatible deserialization") {
        val jsonWithoutCorrelation = """{"tradeId":"t-1","portfolioId":"port-1","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"100","priceAmount":"150.00","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}"""
        val event = Json.decodeFromString<TradeEvent>(jsonWithoutCorrelation)

        event.correlationId shouldBe null
    }
})
