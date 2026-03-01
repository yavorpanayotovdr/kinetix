package com.kinetix.schema

import com.kinetix.common.kafka.events.ComponentBreakdownEvent
import com.kinetix.common.kafka.events.RiskResultEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class RiskResultSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("RiskResultEvent serializes and deserializes all fields correctly") {
        val event = RiskResultEvent(
            portfolioId = "port-1",
            calculationType = "PARAMETRIC",
            confidenceLevel = "CL_95",
            varValue = "25000.0",
            expectedShortfall = "31000.0",
            componentBreakdown = listOf(
                ComponentBreakdownEvent(
                    assetClass = "EQUITY",
                    varContribution = "20000.0",
                    percentageOfTotal = "80.0",
                ),
            ),
            calculatedAt = "2025-01-15T10:00:00Z",
            correlationId = "corr-123",
        )
        val serialized = Json.encodeToString(RiskResultEvent.serializer(), event)

        val deserialized = json.decodeFromString<RiskResultEvent>(serialized)
        deserialized.portfolioId shouldBe "port-1"
        deserialized.varValue shouldBe "25000.0"
        deserialized.expectedShortfall shouldBe "31000.0"
        deserialized.calculationType shouldBe "PARAMETRIC"
        deserialized.correlationId shouldBe "corr-123"
        deserialized.componentBreakdown.size shouldBe 1
        deserialized.componentBreakdown[0].assetClass shouldBe "EQUITY"
    }

    test("RiskResultEvent with empty component breakdown deserializes correctly") {
        val event = RiskResultEvent(
            portfolioId = "port-2",
            calculationType = "HISTORICAL",
            confidenceLevel = "CL_99",
            varValue = "0.0",
            expectedShortfall = "0.0",
            componentBreakdown = emptyList(),
            calculatedAt = "2025-01-15T12:00:00Z",
        )
        val serialized = Json.encodeToString(RiskResultEvent.serializer(), event)

        val deserialized = json.decodeFromString<RiskResultEvent>(serialized)
        deserialized.portfolioId shouldBe "port-2"
        deserialized.varValue shouldBe "0.0"
        deserialized.correlationId shouldBe null
    }
})
