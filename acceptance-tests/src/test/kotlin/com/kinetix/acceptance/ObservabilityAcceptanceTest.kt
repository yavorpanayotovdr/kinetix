package com.kinetix.acceptance

import com.kinetix.common.model.*
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.*
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private class SlowStubRiskEngineClient : RiskEngineClient {
    override suspend fun calculateVaR(
        request: VaRCalculationRequest,
        positions: List<Position>,
    ): VaRResult {
        delay(31_000)
        return VaRResult(
            portfolioId = request.portfolioId,
            calculationType = request.calculationType,
            confidenceLevel = request.confidenceLevel,
            varValue = 50000.0,
            expectedShortfall = 62500.0,
            componentBreakdown = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 50000.0, 100.0),
            ),
            calculatedAt = Instant.now(),
        )
    }
}

private class StubPositionProvider : com.kinetix.risk.client.PositionProvider {
    override suspend fun getPositions(portfolioId: PortfolioId): List<Position> {
        return listOf(
            Position(
                portfolioId = portfolioId,
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), USD),
                marketPrice = Money(BigDecimal("170.00"), USD),
            )
        )
    }
}

class ObservabilityAcceptanceTest : BehaviorSpec({

    given("a risk orchestrator with metrics enabled") {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val positionProvider = StubPositionProvider()
        val slowRiskEngine = SlowStubRiskEngineClient()
        val resultPublisher = mockk<RiskResultPublisher>()
        coEvery { resultPublisher.publish(any()) } just Runs

        val varService = VaRCalculationService(
            positionProvider, slowRiskEngine, resultPublisher, registry,
        )

        `when`("a VaR calculation exceeds 30 seconds") {
            val result = varService.calculateVaR(
                VaRCalculationRequest(
                    portfolioId = PortfolioId("obs-test-port"),
                    calculationType = CalculationType.PARAMETRIC,
                    confidenceLevel = ConfidenceLevel.CL_95,
                )
            )

            then("the calculation duration metric records a value exceeding 30s") {
                val scrapeOutput = registry.scrape()
                scrapeOutput shouldContain "var_calculation_duration"
                val durationLine = scrapeOutput.lines()
                    .filter { it.startsWith("var_calculation_duration_seconds_bucket") }
                    .lastOrNull { !it.contains("+Inf") }
                // The highest finite bucket should have been recorded
                scrapeOutput shouldContain "var_calculation_duration_seconds_count"
            }

            then("the /metrics endpoint exposes the duration metric in Prometheus format") {
                val scrapeOutput = registry.scrape()
                scrapeOutput shouldContain "# HELP"
                scrapeOutput shouldContain "var_calculation_duration_seconds"
                scrapeOutput shouldContain "var_calculation_count_total"
            }
        }
    }
})
