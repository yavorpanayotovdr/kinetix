package com.kinetix.schema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class RiskResultSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("risk-orchestrator RiskResultEvent serializes to schema notification-service can deserialize") {
        val orchestratorEvent = com.kinetix.risk.kafka.RiskResultEvent(
            portfolioId = "port-1",
            calculationType = "PARAMETRIC",
            confidenceLevel = "CL_95",
            varValue = "25000.0",
            expectedShortfall = "31000.0",
            componentBreakdown = listOf(
                com.kinetix.risk.kafka.ComponentBreakdownEvent(
                    assetClass = "EQUITY",
                    varContribution = "20000.0",
                    percentageOfTotal = "80.0",
                ),
            ),
            calculatedAt = "2025-01-15T10:00:00Z",
            correlationId = "corr-123",
        )
        val serialized = Json.encodeToString(com.kinetix.risk.kafka.RiskResultEvent.serializer(), orchestratorEvent)

        val notificationEvent = json.decodeFromString<com.kinetix.notification.model.RiskResultEvent>(serialized)
        notificationEvent.portfolioId shouldBe "port-1"
        notificationEvent.varValue shouldBe "25000.0"
        notificationEvent.expectedShortfall shouldBe "31000.0"
        notificationEvent.calculationType shouldBe "PARAMETRIC"
        notificationEvent.correlationId shouldBe "corr-123"
    }

    test("notification-service can handle RiskResultEvent with all fields from risk-orchestrator") {
        val orchestratorEvent = com.kinetix.risk.kafka.RiskResultEvent(
            portfolioId = "port-2",
            calculationType = "HISTORICAL",
            confidenceLevel = "CL_99",
            varValue = "0.0",
            expectedShortfall = "0.0",
            componentBreakdown = emptyList(),
            calculatedAt = "2025-01-15T12:00:00Z",
        )
        val serialized = Json.encodeToString(com.kinetix.risk.kafka.RiskResultEvent.serializer(), orchestratorEvent)

        val notificationEvent = json.decodeFromString<com.kinetix.notification.model.RiskResultEvent>(serialized)
        notificationEvent.portfolioId shouldBe "port-2"
        notificationEvent.varValue shouldBe "0.0"
        notificationEvent.correlationId shouldBe null
    }
})
