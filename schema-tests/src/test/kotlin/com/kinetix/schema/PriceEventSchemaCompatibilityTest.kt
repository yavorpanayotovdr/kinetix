package com.kinetix.schema

import com.kinetix.common.kafka.events.PriceEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class PriceEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("PriceEvent serializes and deserializes all fields correctly") {
        val event = PriceEvent(
            instrumentId = "AAPL",
            priceAmount = "155.75",
            priceCurrency = "USD",
            timestamp = "2025-01-15T10:00:00Z",
            source = "MARKET",
            correlationId = "corr-price-1",
        )
        val serialized = Json.encodeToString(PriceEvent.serializer(), event)

        val deserialized = json.decodeFromString<PriceEvent>(serialized)
        deserialized.instrumentId shouldBe "AAPL"
        deserialized.priceAmount shouldBe "155.75"
        deserialized.priceCurrency shouldBe "USD"
        deserialized.timestamp shouldBe "2025-01-15T10:00:00Z"
        deserialized.source shouldBe "MARKET"
        deserialized.correlationId shouldBe "corr-price-1"
    }

    test("PriceEvent schema is stable across multiple field values") {
        val event = PriceEvent(
            instrumentId = "MSFT",
            priceAmount = "300.50",
            priceCurrency = "USD",
            timestamp = "2025-01-15T11:00:00Z",
            source = "CALCULATED",
            correlationId = "corr-price-2",
        )
        val serialized = Json.encodeToString(PriceEvent.serializer(), event)

        val deserialized = json.decodeFromString<PriceEvent>(serialized)
        deserialized.instrumentId shouldBe "MSFT"
        deserialized.priceAmount shouldBe "300.50"
        deserialized.source shouldBe "CALCULATED"
        deserialized.correlationId shouldBe "corr-price-2"
    }

    test("PriceEvent round-trips through JSON without data loss") {
        val original = PriceEvent(
            instrumentId = "GOOG",
            priceAmount = "180.25",
            priceCurrency = "USD",
            timestamp = "2025-01-15T12:00:00Z",
            source = "MARKET",
            correlationId = "corr-price-3",
        )

        val serialized = Json.encodeToString(PriceEvent.serializer(), original)
        val deserialized = json.decodeFromString<PriceEvent>(serialized)

        deserialized shouldBe original
    }
})
