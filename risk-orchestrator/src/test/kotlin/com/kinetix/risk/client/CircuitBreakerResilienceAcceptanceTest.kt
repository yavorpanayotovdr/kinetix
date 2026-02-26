package com.kinetix.risk.client

import com.kinetix.common.model.*
import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.common.resilience.CircuitBreakerConfig
import com.kinetix.common.resilience.CircuitBreakerOpenException
import com.kinetix.common.resilience.CircuitState
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class CircuitBreakerResilienceAcceptanceTest : BehaviorSpec({

    given("circuit breaker protecting risk engine calls") {

        `when`("risk engine fails repeatedly exceeding threshold") {
            val delegate = mockk<RiskEngineClient>()
            val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3, resetTimeoutMs = 5000, name = "risk"))
            val resilient = ResilientRiskEngineClient(delegate, cb)
            val request = VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
            coEvery { delegate.calculateVaR(any(), any()) } throws RuntimeException("connection refused")

            then("circuit opens and rejects subsequent calls") {
                repeat(3) { runCatching { resilient.calculateVaR(request, emptyList()) } }
                cb.currentState shouldBe CircuitState.OPEN
                shouldThrow<CircuitBreakerOpenException> {
                    resilient.calculateVaR(request, emptyList())
                }
            }

            then("after reset timeout, circuit transitions to half-open") {
                cb.reset()
                repeat(3) { runCatching { resilient.calculateVaR(request, emptyList()) } }
                cb.currentState shouldBe CircuitState.OPEN
            }

            then("successful call in half-open closes circuit") {
                cb.reset()
                val result = VaRResult(
                    portfolioId = PortfolioId("port-1"),
                    calculationType = CalculationType.PARAMETRIC,
                    confidenceLevel = ConfidenceLevel.CL_95,
                    varValue = 50000.0,
                    expectedShortfall = 65000.0,
                    componentBreakdown = emptyList(),
                    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
                )
                coEvery { delegate.calculateVaR(any(), any()) } returns result
                val actual = resilient.calculateVaR(request, emptyList())
                actual shouldBe result
                cb.currentState shouldBe CircuitState.CLOSED
            }
        }
    }
})
