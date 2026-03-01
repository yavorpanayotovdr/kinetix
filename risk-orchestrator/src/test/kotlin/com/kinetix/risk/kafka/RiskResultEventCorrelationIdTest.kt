package com.kinetix.risk.kafka

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private fun valuationResult(portfolioId: String = "port-1") = ValuationResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    componentBreakdown = listOf(
        ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
    ),
    greeks = null,
    calculatedAt = Instant.parse("2025-01-15T10:30:00Z"),
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
)

class RiskResultEventCorrelationIdTest : FunSpec({

    test("RiskResultEvent carries correlationId from trigger") {
        val event = RiskResultEvent.from(valuationResult(), correlationId = "trigger-corr-123")

        event.correlationId shouldBe "trigger-corr-123"
    }

    test("RiskResultEvent correlationId defaults to null") {
        val event = RiskResultEvent.from(valuationResult())

        event.correlationId shouldBe null
    }

    test("correlationId survives JSON round-trip") {
        val event = RiskResultEvent.from(valuationResult(), correlationId = "round-trip-id")
        val json = Json.encodeToString(event)
        val deserialized = Json.decodeFromString<RiskResultEvent>(json)

        deserialized.correlationId shouldBe "round-trip-id"
    }

    test("backward-compatible deserialization without correlationId field") {
        val json = """{"portfolioId":"port-1","calculationType":"PARAMETRIC","confidenceLevel":"CL_95","varValue":"5000.0","expectedShortfall":"6250.0","componentBreakdown":[],"calculatedAt":"2025-01-15T10:30:00Z"}"""
        val event = Json.decodeFromString<RiskResultEvent>(json)

        event.correlationId shouldBe null
    }
})
