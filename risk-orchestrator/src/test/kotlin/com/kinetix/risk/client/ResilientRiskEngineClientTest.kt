package com.kinetix.risk.client

import com.kinetix.common.model.*
import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.common.resilience.CircuitBreakerConfig
import com.kinetix.common.resilience.CircuitBreakerOpenException
import com.kinetix.common.resilience.CircuitState
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.ValuationOutput
import com.kinetix.risk.model.ValuationResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class ResilientRiskEngineClientTest : FunSpec({

    val delegate = mockk<RiskEngineClient>()
    val circuitBreaker = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 2, name = "risk-engine"))
    val client = ResilientRiskEngineClient(delegate, circuitBreaker)

    val request = VaRCalculationRequest(
        portfolioId = PortfolioId("port-1"),
        calculationType = CalculationType.PARAMETRIC,
        confidenceLevel = ConfidenceLevel.CL_95,
    )
    val positions = emptyList<Position>()
    val result = ValuationResult(
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

    beforeEach {
        clearMocks(delegate)
        circuitBreaker.reset()
    }

    test("delegates to underlying client on success") {
        coEvery { delegate.valuate(request, positions) } returns result
        val actual = client.valuate(request, positions)
        actual shouldBe result
        coVerify(exactly = 1) { delegate.valuate(request, positions) }
    }

    test("wraps failures in circuit breaker") {
        coEvery { delegate.valuate(request, positions) } throws RuntimeException("connection refused")
        runCatching { client.valuate(request, positions) }
        circuitBreaker.currentState shouldBe CircuitState.CLOSED

        runCatching { client.valuate(request, positions) }
        circuitBreaker.currentState shouldBe CircuitState.OPEN
    }

    test("rejects calls when circuit is open") {
        coEvery { delegate.valuate(request, positions) } throws RuntimeException("connection refused")
        repeat(2) {
            runCatching { client.valuate(request, positions) }
        }
        circuitBreaker.currentState shouldBe CircuitState.OPEN

        shouldThrow<CircuitBreakerOpenException> {
            client.valuate(request, positions)
        }
    }
})
