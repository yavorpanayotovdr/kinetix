package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PositionPnlAttribution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode

private fun bd(value: String) = BigDecimal(value)

class PnlAttributionServiceTest : FunSpec({

    val service = PnlAttributionService()

    test("computes Taylor expansion decomposition with known inputs") {
        // Position: delta=0.5, gamma=0.1, vega=200, theta=-50, rho=30
        // Market moves: priceChange=2.0, volChange=0.01, rateChange=0.005
        // Expected:
        //   deltaPnl  = 0.5 * 2.0          = 1.0
        //   gammaPnl  = 0.5 * 0.1 * 2.0^2  = 0.2
        //   vegaPnl   = 200 * 0.01          = 2.0
        //   thetaPnl  = -50 * (1/252)       = -0.198412...
        //   rhoPnl    = 30 * 0.005          = 0.15
        val input = PositionPnlInput(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("3.5"),
            delta = bd("0.5"),
            gamma = bd("0.1"),
            vega = bd("200"),
            theta = bd("-50"),
            rho = bd("30"),
            priceChange = bd("2.0"),
            volChange = bd("0.01"),
            rateChange = bd("0.005"),
        )

        val result = service.attribute(
            portfolioId = PortfolioId("port-1"),
            positions = listOf(input),
        )

        result.positionAttributions shouldHaveSize 1
        val pos = result.positionAttributions[0]

        pos.deltaPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("1.000000")
        pos.gammaPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("0.200000")
        pos.vegaPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("2.000000")
        pos.thetaPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("-0.198413")
        pos.rhoPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("0.150000")
    }

    test("unexplained equals total minus sum of explained components") {
        val input = PositionPnlInput(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("10.0"),
            delta = bd("0.5"),
            gamma = bd("0.1"),
            vega = bd("200"),
            theta = bd("-50"),
            rho = bd("30"),
            priceChange = bd("2.0"),
            volChange = bd("0.01"),
            rateChange = bd("0.005"),
        )

        val result = service.attribute(
            portfolioId = PortfolioId("port-1"),
            positions = listOf(input),
        )

        val pos = result.positionAttributions[0]
        val explained = pos.deltaPnl + pos.gammaPnl + pos.vegaPnl + pos.thetaPnl + pos.rhoPnl
        pos.unexplainedPnl.setScale(6, RoundingMode.HALF_UP) shouldBe
                (bd("10.0") - explained).setScale(6, RoundingMode.HALF_UP)

        // Also verify the portfolio-level unexplained
        val portfolioExplained = result.deltaPnl + result.gammaPnl + result.vegaPnl + result.thetaPnl + result.rhoPnl
        result.unexplainedPnl.setScale(6, RoundingMode.HALF_UP) shouldBe
                (result.totalPnl - portfolioExplained).setScale(6, RoundingMode.HALF_UP)
    }

    test("with zero greeks all P&L goes to unexplained") {
        val input = PositionPnlInput(
            instrumentId = InstrumentId("SPY"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("5.0"),
            delta = BigDecimal.ZERO,
            gamma = BigDecimal.ZERO,
            vega = BigDecimal.ZERO,
            theta = BigDecimal.ZERO,
            rho = BigDecimal.ZERO,
            priceChange = bd("3.0"),
            volChange = bd("0.02"),
            rateChange = bd("0.001"),
        )

        val result = service.attribute(
            portfolioId = PortfolioId("port-1"),
            positions = listOf(input),
        )

        val pos = result.positionAttributions[0]
        pos.deltaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.gammaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.vegaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.thetaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.rhoPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.unexplainedPnl.compareTo(bd("5.0")) shouldBe 0
        pos.totalPnl.compareTo(bd("5.0")) shouldBe 0

        result.unexplainedPnl.compareTo(bd("5.0")) shouldBe 0
    }

    test("with zero price change only theta contributes") {
        val input = PositionPnlInput(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("-0.198413"),
            delta = bd("0.5"),
            gamma = bd("0.1"),
            vega = bd("200"),
            theta = bd("-50"),
            rho = bd("30"),
            priceChange = BigDecimal.ZERO,
            volChange = BigDecimal.ZERO,
            rateChange = BigDecimal.ZERO,
        )

        val result = service.attribute(
            portfolioId = PortfolioId("port-1"),
            positions = listOf(input),
        )

        val pos = result.positionAttributions[0]
        pos.deltaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.gammaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.vegaPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        pos.rhoPnl.compareTo(BigDecimal.ZERO) shouldBe 0
        // theta = -50 * (1/252) = -0.198412698...
        pos.thetaPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("-0.198413")
        pos.unexplainedPnl.setScale(6, RoundingMode.HALF_UP) shouldBe bd("0.000000")
    }

    test("position-level attributions sum to portfolio totals") {
        val inputs = listOf(
            PositionPnlInput(
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                totalPnl = bd("5.0"),
                delta = bd("0.5"),
                gamma = bd("0.1"),
                vega = bd("200"),
                theta = bd("-50"),
                rho = bd("30"),
                priceChange = bd("2.0"),
                volChange = bd("0.01"),
                rateChange = bd("0.005"),
            ),
            PositionPnlInput(
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                totalPnl = bd("3.0"),
                delta = bd("0.3"),
                gamma = bd("0.05"),
                vega = bd("100"),
                theta = bd("-20"),
                rho = bd("10"),
                priceChange = bd("1.5"),
                volChange = bd("0.02"),
                rateChange = bd("0.003"),
            ),
            PositionPnlInput(
                instrumentId = InstrumentId("UST10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                totalPnl = bd("-1.0"),
                delta = bd("-0.2"),
                gamma = bd("0.01"),
                vega = BigDecimal.ZERO,
                theta = bd("-5"),
                rho = bd("50"),
                priceChange = bd("-0.5"),
                volChange = BigDecimal.ZERO,
                rateChange = bd("0.01"),
            ),
        )

        val result = service.attribute(
            portfolioId = PortfolioId("port-1"),
            positions = inputs,
        )

        result.positionAttributions shouldHaveSize 3

        // Sum of position-level deltas should equal portfolio delta
        val sumDelta = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.deltaPnl }
        result.deltaPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumDelta.setScale(10, RoundingMode.HALF_UP)

        val sumGamma = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.gammaPnl }
        result.gammaPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumGamma.setScale(10, RoundingMode.HALF_UP)

        val sumVega = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.vegaPnl }
        result.vegaPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumVega.setScale(10, RoundingMode.HALF_UP)

        val sumTheta = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.thetaPnl }
        result.thetaPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumTheta.setScale(10, RoundingMode.HALF_UP)

        val sumRho = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.rhoPnl }
        result.rhoPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumRho.setScale(10, RoundingMode.HALF_UP)

        val sumTotal = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.totalPnl }
        result.totalPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumTotal.setScale(10, RoundingMode.HALF_UP)

        val sumUnexplained = result.positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc + p.unexplainedPnl }
        result.unexplainedPnl.setScale(10, RoundingMode.HALF_UP) shouldBe sumUnexplained.setScale(10, RoundingMode.HALF_UP)
    }
})
