package com.kinetix.risk.simulation

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.ValuationOutput
import com.kinetix.risk.model.ValuationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlin.system.measureTimeMillis

class DelayingRiskEngineClientTest : FunSpec({

    val delegate = mockk<RiskEngineClient>()
    val discoverDelay = 100L..200L
    val calculateDelay = 150L..250L
    val client = DelayingRiskEngineClient(delegate, discoverDelay, calculateDelay)

    val positions = emptyList<Position>()
    val request = VaRCalculationRequest(
        portfolioId = PortfolioId("port-1"),
        calculationType = CalculationType.PARAMETRIC,
        confidenceLevel = ConfidenceLevel.CL_95,
    )
    val valuationResult = ValuationResult(
        portfolioId = PortfolioId("port-1"),
        calculationType = CalculationType.PARAMETRIC,
        confidenceLevel = ConfidenceLevel.CL_95,
        varValue = 50000.0,
        expectedShortfall = 65000.0,
        componentBreakdown = emptyList(),
        greeks = null,
        calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
        computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
    )
    val dependenciesResponse = DataDependenciesResponse.getDefaultInstance()

    test("delegates discoverDependencies and returns result") {
        coEvery { delegate.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns dependenciesResponse

        val result = client.discoverDependencies(positions, "PARAMETRIC", "CL_95")

        result shouldBe dependenciesResponse
    }

    test("applies delay to discoverDependencies") {
        coEvery { delegate.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns dependenciesResponse

        val elapsed = measureTimeMillis {
            client.discoverDependencies(positions, "PARAMETRIC", "CL_95")
        }

        elapsed shouldBeGreaterThanOrEqual discoverDelay.first
    }

    test("delegates valuate and returns result") {
        coEvery { delegate.valuate(request, positions) } returns valuationResult

        val result = client.valuate(request, positions)

        result shouldBe valuationResult
    }

    test("applies delay to valuate") {
        coEvery { delegate.valuate(request, positions) } returns valuationResult

        val elapsed = measureTimeMillis {
            client.valuate(request, positions)
        }

        elapsed shouldBeGreaterThanOrEqual calculateDelay.first
    }
})
