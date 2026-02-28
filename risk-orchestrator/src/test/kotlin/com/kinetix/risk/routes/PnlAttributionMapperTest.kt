package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private fun bd(value: String) = BigDecimal(value)

class PnlAttributionMapperTest : FunSpec({

    test("maps PnlAttribution domain to PnlAttributionResponse DTO") {
        val attribution = PnlAttribution(
            portfolioId = PortfolioId("port-1"),
            date = LocalDate.of(2025, 1, 15),
            totalPnl = bd("10.00"),
            deltaPnl = bd("3.00"),
            gammaPnl = bd("1.50"),
            vegaPnl = bd("2.00"),
            thetaPnl = bd("-0.50"),
            rhoPnl = bd("0.30"),
            unexplainedPnl = bd("3.70"),
            positionAttributions = listOf(
                PositionPnlAttribution(
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    totalPnl = bd("7.00"),
                    deltaPnl = bd("2.00"),
                    gammaPnl = bd("1.00"),
                    vegaPnl = bd("1.50"),
                    thetaPnl = bd("-0.30"),
                    rhoPnl = bd("0.20"),
                    unexplainedPnl = bd("2.60"),
                ),
            ),
            calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )

        val response = attribution.toResponse()

        response.portfolioId shouldBe "port-1"
        response.date shouldBe "2025-01-15"
        response.totalPnl shouldBe "10.00"
        response.deltaPnl shouldBe "3.00"
        response.gammaPnl shouldBe "1.50"
        response.vegaPnl shouldBe "2.00"
        response.thetaPnl shouldBe "-0.50"
        response.rhoPnl shouldBe "0.30"
        response.unexplainedPnl shouldBe "3.70"
        response.calculatedAt shouldBe "2025-01-15T10:00:00Z"
    }

    test("maps position attributions to DTOs") {
        val attribution = PnlAttribution(
            portfolioId = PortfolioId("port-1"),
            date = LocalDate.of(2025, 1, 15),
            totalPnl = bd("10.00"),
            deltaPnl = bd("3.00"),
            gammaPnl = bd("1.50"),
            vegaPnl = bd("2.00"),
            thetaPnl = bd("-0.50"),
            rhoPnl = bd("0.30"),
            unexplainedPnl = bd("3.70"),
            positionAttributions = listOf(
                PositionPnlAttribution(
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    totalPnl = bd("7.00"),
                    deltaPnl = bd("2.00"),
                    gammaPnl = bd("1.00"),
                    vegaPnl = bd("1.50"),
                    thetaPnl = bd("-0.30"),
                    rhoPnl = bd("0.20"),
                    unexplainedPnl = bd("2.60"),
                ),
                PositionPnlAttribution(
                    instrumentId = InstrumentId("MSFT"),
                    assetClass = AssetClass.EQUITY,
                    totalPnl = bd("3.00"),
                    deltaPnl = bd("1.00"),
                    gammaPnl = bd("0.50"),
                    vegaPnl = bd("0.50"),
                    thetaPnl = bd("-0.20"),
                    rhoPnl = bd("0.10"),
                    unexplainedPnl = bd("1.10"),
                ),
            ),
            calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )

        val response = attribution.toResponse()

        response.positionAttributions shouldHaveSize 2

        val aapl = response.positionAttributions[0]
        aapl.instrumentId shouldBe "AAPL"
        aapl.assetClass shouldBe "EQUITY"
        aapl.totalPnl shouldBe "7.00"
        aapl.deltaPnl shouldBe "2.00"
        aapl.gammaPnl shouldBe "1.00"
        aapl.vegaPnl shouldBe "1.50"
        aapl.thetaPnl shouldBe "-0.30"
        aapl.rhoPnl shouldBe "0.20"
        aapl.unexplainedPnl shouldBe "2.60"

        val msft = response.positionAttributions[1]
        msft.instrumentId shouldBe "MSFT"
        msft.assetClass shouldBe "EQUITY"
        msft.totalPnl shouldBe "3.00"
    }

    test("maps empty position attributions list") {
        val attribution = PnlAttribution(
            portfolioId = PortfolioId("port-1"),
            date = LocalDate.of(2025, 1, 15),
            totalPnl = bd("0.00"),
            deltaPnl = bd("0.00"),
            gammaPnl = bd("0.00"),
            vegaPnl = bd("0.00"),
            thetaPnl = bd("0.00"),
            rhoPnl = bd("0.00"),
            unexplainedPnl = bd("0.00"),
            positionAttributions = emptyList(),
            calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )

        val response = attribution.toResponse()
        response.positionAttributions shouldHaveSize 0
    }

    test("maps PositionPnlAttribution to DTO directly") {
        val pos = PositionPnlAttribution(
            instrumentId = InstrumentId("SPY"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("5.50"),
            deltaPnl = bd("2.25"),
            gammaPnl = bd("0.75"),
            vegaPnl = bd("1.00"),
            thetaPnl = bd("-0.25"),
            rhoPnl = bd("0.10"),
            unexplainedPnl = bd("1.65"),
        )

        val dto = pos.toDto()
        dto.instrumentId shouldBe "SPY"
        dto.assetClass shouldBe "EQUITY"
        dto.totalPnl shouldBe "5.50"
        dto.deltaPnl shouldBe "2.25"
        dto.gammaPnl shouldBe "0.75"
        dto.vegaPnl shouldBe "1.00"
        dto.thetaPnl shouldBe "-0.25"
        dto.rhoPnl shouldBe "0.10"
        dto.unexplainedPnl shouldBe "1.65"
    }
})
