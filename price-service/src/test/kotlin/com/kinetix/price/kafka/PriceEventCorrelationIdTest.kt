package com.kinetix.price.kafka

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

private fun pricePoint() = PricePoint(
    instrumentId = InstrumentId("AAPL"),
    price = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
    timestamp = Instant.parse("2025-01-15T10:00:00Z"),
    source = PriceSource.EXCHANGE,
)

class PriceEventCorrelationIdTest : FunSpec({

    test("PriceEvent.from generates correlationId when not provided") {
        val event = PriceEvent.from(pricePoint())

        event.correlationId.shouldNotBeNull()
        event.correlationId!! shouldMatch UUID_REGEX
    }

    test("PriceEvent.from uses provided correlationId") {
        val event = PriceEvent.from(pricePoint(), correlationId = "price-corr-123")

        event.correlationId shouldBe "price-corr-123"
    }

    test("correlationId defaults to null for backward-compatible deserialization") {
        val json = """{"instrumentId":"AAPL","priceAmount":"150.00","priceCurrency":"USD","timestamp":"2025-01-15T10:00:00Z","source":"EXCHANGE"}"""
        val event = Json.decodeFromString<PriceEvent>(json)

        event.correlationId shouldBe null
    }
})
