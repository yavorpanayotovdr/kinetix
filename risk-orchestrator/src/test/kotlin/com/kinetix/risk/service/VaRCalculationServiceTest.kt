package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun position(
    portfolioId: String = "port-1",
    instrumentId: String = "AAPL",
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    marketPrice: String = "170.00",
) = Position(
    portfolioId = PortfolioId(portfolioId),
    instrumentId = InstrumentId(instrumentId),
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = Money(BigDecimal("150.00"), USD),
    marketPrice = Money(BigDecimal(marketPrice), USD),
)

private fun varResult(
    portfolioId: String = "port-1",
    calculationType: CalculationType = CalculationType.PARAMETRIC,
    varValue: Double = 5000.0,
    componentBreakdown: List<ComponentBreakdown> = listOf(
        ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
    ),
) = VaRResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = calculationType,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    componentBreakdown = componentBreakdown,
    calculatedAt = Instant.now(),
)

class VaRCalculationServiceTest : FunSpec({

    val positionProvider = mockk<PositionProvider>()
    val riskEngineClient = mockk<RiskEngineClient>()
    val resultPublisher = mockk<RiskResultPublisher>()
    val service = VaRCalculationService(positionProvider, riskEngineClient, resultPublisher)

    beforeEach {
        clearMocks(positionProvider, riskEngineClient, resultPublisher)
    }

    test("fetches positions, calls risk engine, and publishes result") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe expectedResult

        coVerify(ordering = Ordering.ORDERED) {
            positionProvider.getPositions(PortfolioId("port-1"))
            riskEngineClient.calculateVaR(any(), positions)
            resultPublisher.publish(expectedResult)
        }
    }

    test("returns null and does not call risk engine for empty portfolio") {
        coEvery { positionProvider.getPositions(PortfolioId("empty")) } returns emptyList()

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("empty"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe null

        coVerify(exactly = 0) { riskEngineClient.calculateVaR(any(), any()) }
        coVerify(exactly = 0) { resultPublisher.publish(any()) }
    }

    test("passes correct calculation type to risk engine") {
        val positions = listOf(position())

        for (calcType in CalculationType.entries) {
            val expectedResult = varResult(calculationType = calcType)

            coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
            coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
            coEvery { resultPublisher.publish(any()) } just Runs

            val result = service.calculateVaR(
                VaRCalculationRequest(
                    portfolioId = PortfolioId("port-1"),
                    calculationType = calcType,
                    confidenceLevel = ConfidenceLevel.CL_95,
                )
            )

            result!!.calculationType shouldBe calcType
        }
    }

    test("handles multi-asset portfolio") {
        val positions = listOf(
            position(instrumentId = "AAPL", assetClass = AssetClass.EQUITY),
            position(instrumentId = "UST10Y", assetClass = AssetClass.FIXED_INCOME),
            position(instrumentId = "EURUSD", assetClass = AssetClass.FX),
        )
        val expectedResult = varResult(
            componentBreakdown = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 3000.0, 50.0),
                ComponentBreakdown(AssetClass.FIXED_INCOME, 1500.0, 25.0),
                ComponentBreakdown(AssetClass.FX, 1500.0, 25.0),
            ),
        )

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(any()) } just Runs

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result!!.componentBreakdown.size shouldBe 3
    }
})
