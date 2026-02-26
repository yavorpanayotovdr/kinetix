package com.kinetix.risk.simulation

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
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
    val varResult = VaRResult(
        portfolioId = PortfolioId("port-1"),
        calculationType = CalculationType.PARAMETRIC,
        confidenceLevel = ConfidenceLevel.CL_95,
        varValue = 50000.0,
        expectedShortfall = 65000.0,
        componentBreakdown = emptyList(),
        calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
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

    test("delegates calculateVaR and returns result") {
        coEvery { delegate.calculateVaR(request, positions) } returns varResult

        val result = client.calculateVaR(request, positions)

        result shouldBe varResult
    }

    test("applies delay to calculateVaR") {
        coEvery { delegate.calculateVaR(request, positions) } returns varResult

        val elapsed = measureTimeMillis {
            client.calculateVaR(request, positions)
        }

        elapsed shouldBeGreaterThanOrEqual calculateDelay.first
    }
})
