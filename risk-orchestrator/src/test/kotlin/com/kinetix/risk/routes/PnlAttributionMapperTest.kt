package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.AttributionDataQuality
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private fun bd(value: String) = BigDecimal(value)

private fun sampleAttribution(
    bookId: BookId = BookId("port-1"),
    date: LocalDate = LocalDate.of(2025, 1, 15),
    dataQualityFlag: AttributionDataQuality = AttributionDataQuality.FULL_ATTRIBUTION,
) = PnlAttribution(
    bookId = bookId,
    date = date,
    totalPnl = bd("10.00"),
    deltaPnl = bd("3.00"),
    gammaPnl = bd("1.50"),
    vegaPnl = bd("2.00"),
    thetaPnl = bd("-0.50"),
    rhoPnl = bd("0.30"),
    vannaPnl = bd("0.10"),
    volgaPnl = bd("0.04"),
    charmPnl = bd("-0.002"),
    crossGammaPnl = bd("0.00"),
    unexplainedPnl = bd("3.562"),
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
            vannaPnl = bd("0.07"),
            volgaPnl = bd("0.025"),
            charmPnl = bd("-0.001"),
            crossGammaPnl = bd("0.00"),
            unexplainedPnl = bd("2.506"),
        ),
    ),
    dataQualityFlag = dataQualityFlag,
    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class PnlAttributionMapperTest : FunSpec({

    test("maps PnlAttribution domain to PnlAttributionResponse DTO including cross-Greek fields") {
        val attribution = sampleAttribution()
        val response = attribution.toResponse()

        response.bookId shouldBe "port-1"
        response.date shouldBe "2025-01-15"
        response.totalPnl shouldBe "10.00"
        response.deltaPnl shouldBe "3.00"
        response.gammaPnl shouldBe "1.50"
        response.vegaPnl shouldBe "2.00"
        response.thetaPnl shouldBe "-0.50"
        response.rhoPnl shouldBe "0.30"
        response.vannaPnl shouldBe "0.10"
        response.volgaPnl shouldBe "0.04"
        response.charmPnl shouldBe "-0.002"
        response.crossGammaPnl shouldBe "0.00"
        response.unexplainedPnl shouldBe "3.562"
        response.dataQualityFlag shouldBe "FULL_ATTRIBUTION"
        response.calculatedAt shouldBe "2025-01-15T10:00:00Z"
    }

    test("maps position attributions to DTOs including cross-Greek fields") {
        val attribution = sampleAttribution()
        val response = attribution.toResponse()

        response.positionAttributions shouldHaveSize 1
        val aapl = response.positionAttributions[0]
        aapl.instrumentId shouldBe "AAPL"
        aapl.assetClass shouldBe "EQUITY"
        aapl.totalPnl shouldBe "7.00"
        aapl.deltaPnl shouldBe "2.00"
        aapl.gammaPnl shouldBe "1.00"
        aapl.vegaPnl shouldBe "1.50"
        aapl.thetaPnl shouldBe "-0.30"
        aapl.rhoPnl shouldBe "0.20"
        aapl.vannaPnl shouldBe "0.07"
        aapl.volgaPnl shouldBe "0.025"
        aapl.charmPnl shouldBe "-0.001"
        aapl.crossGammaPnl shouldBe "0.00"
        aapl.unexplainedPnl shouldBe "2.506"
    }

    test("maps empty position attributions list") {
        val attribution = PnlAttribution(
            bookId = BookId("port-1"),
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
        response.dataQualityFlag shouldBe "PRICE_ONLY"
    }

    test("maps PositionPnlAttribution to DTO directly including cross-Greek fields") {
        val pos = PositionPnlAttribution(
            instrumentId = InstrumentId("SPY"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("5.50"),
            deltaPnl = bd("2.25"),
            gammaPnl = bd("0.75"),
            vegaPnl = bd("1.00"),
            thetaPnl = bd("-0.25"),
            rhoPnl = bd("0.10"),
            vannaPnl = bd("0.05"),
            volgaPnl = bd("0.02"),
            charmPnl = bd("-0.001"),
            crossGammaPnl = bd("0.00"),
            unexplainedPnl = bd("1.531"),
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
        dto.vannaPnl shouldBe "0.05"
        dto.volgaPnl shouldBe "0.02"
        dto.charmPnl shouldBe "-0.001"
        dto.crossGammaPnl shouldBe "0.00"
        dto.unexplainedPnl shouldBe "1.531"
    }

    test("maps PRICE_ONLY data quality flag correctly") {
        val attribution = sampleAttribution(dataQualityFlag = AttributionDataQuality.PRICE_ONLY)
        attribution.toResponse().dataQualityFlag shouldBe "PRICE_ONLY"
    }

    test("maps STALE_GREEKS data quality flag correctly") {
        val attribution = sampleAttribution(dataQualityFlag = AttributionDataQuality.STALE_GREEKS)
        attribution.toResponse().dataQualityFlag shouldBe "STALE_GREEKS"
    }
})
