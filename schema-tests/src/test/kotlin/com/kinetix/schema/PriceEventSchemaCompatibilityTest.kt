package com.kinetix.schema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class PriceEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("price-service PriceEvent serializes to schema risk-orchestrator can deserialize") {
        val priceEvent = com.kinetix.price.kafka.PriceEvent(
            instrumentId = "AAPL",
            priceAmount = "155.75",
            priceCurrency = "USD",
            timestamp = "2025-01-15T10:00:00Z",
            source = "MARKET",
            correlationId = "corr-price-1",
        )
        val serialized = Json.encodeToString(com.kinetix.price.kafka.PriceEvent.serializer(), priceEvent)

        val riskEvent = json.decodeFromString<com.kinetix.risk.kafka.PriceEvent>(serialized)
        riskEvent.instrumentId shouldBe "AAPL"
        riskEvent.priceAmount shouldBe "155.75"
        riskEvent.priceCurrency shouldBe "USD"
        riskEvent.timestamp shouldBe "2025-01-15T10:00:00Z"
        riskEvent.source shouldBe "MARKET"
        riskEvent.correlationId shouldBe "corr-price-1"
    }

    test("price-service PriceEvent serializes to schema position-service can deserialize") {
        val priceEvent = com.kinetix.price.kafka.PriceEvent(
            instrumentId = "MSFT",
            priceAmount = "300.50",
            priceCurrency = "USD",
            timestamp = "2025-01-15T11:00:00Z",
            source = "CALCULATED",
            correlationId = "corr-price-2",
        )
        val serialized = Json.encodeToString(com.kinetix.price.kafka.PriceEvent.serializer(), priceEvent)

        val positionEvent = json.decodeFromString<com.kinetix.position.kafka.PriceEvent>(serialized)
        positionEvent.instrumentId shouldBe "MSFT"
        positionEvent.priceAmount shouldBe "300.50"
        positionEvent.priceCurrency shouldBe "USD"
        positionEvent.timestamp shouldBe "2025-01-15T11:00:00Z"
        positionEvent.source shouldBe "CALCULATED"
        positionEvent.correlationId shouldBe "corr-price-2"
    }

    test("risk-orchestrator and position-service PriceEvent produce identical JSON for the same field values") {
        val riskEvent = com.kinetix.risk.kafka.PriceEvent(
            instrumentId = "GOOG",
            priceAmount = "180.25",
            priceCurrency = "USD",
            timestamp = "2025-01-15T12:00:00Z",
            source = "MARKET",
            correlationId = "corr-price-3",
        )
        val positionEvent = com.kinetix.position.kafka.PriceEvent(
            instrumentId = "GOOG",
            priceAmount = "180.25",
            priceCurrency = "USD",
            timestamp = "2025-01-15T12:00:00Z",
            source = "MARKET",
            correlationId = "corr-price-3",
        )

        val riskJson = Json.encodeToString(com.kinetix.risk.kafka.PriceEvent.serializer(), riskEvent)
        val positionJson = Json.encodeToString(com.kinetix.position.kafka.PriceEvent.serializer(), positionEvent)

        riskJson shouldBe positionJson
    }
})
