package com.kinetix.risk.mapper

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.Currency

private val USD = Currency.getInstance("USD")

class PositionMapperTest : FunSpec({

    test("maps domain position to proto position with correct fields") {
        val position = Position(
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("100"),
            averageCost = Money(BigDecimal("150.00"), USD),
            marketPrice = Money(BigDecimal("170.00"), USD),
        )

        val proto = position.toProto()

        proto.portfolioId.value shouldBe "port-1"
        proto.instrumentId.value shouldBe "AAPL"
        proto.assetClass shouldBe com.kinetix.proto.common.AssetClass.EQUITY
        proto.quantity shouldBe 100.0
        proto.marketValue.amount shouldBe "17000.00"
        proto.marketValue.currency shouldBe "USD"
    }

    test("maps all asset classes correctly") {
        val assetClassMappings = mapOf(
            AssetClass.EQUITY to com.kinetix.proto.common.AssetClass.EQUITY,
            AssetClass.FIXED_INCOME to com.kinetix.proto.common.AssetClass.FIXED_INCOME,
            AssetClass.FX to com.kinetix.proto.common.AssetClass.FX,
            AssetClass.COMMODITY to com.kinetix.proto.common.AssetClass.COMMODITY,
            AssetClass.DERIVATIVE to com.kinetix.proto.common.AssetClass.DERIVATIVE,
        )

        for ((domain, proto) in assetClassMappings) {
            val position = Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("TEST"),
                assetClass = domain,
                quantity = BigDecimal("10"),
                averageCost = Money(BigDecimal("100.00"), USD),
                marketPrice = Money(BigDecimal("100.00"), USD),
            )
            position.toProto().assetClass shouldBe proto
        }
    }

    test("maps position with negative quantity") {
        val position = Position(
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("-50"),
            averageCost = Money(BigDecimal("150.00"), USD),
            marketPrice = Money(BigDecimal("170.00"), USD),
        )

        val proto = position.toProto()
        proto.quantity shouldBe -50.0
        proto.marketValue.amount shouldBe "-8500.00"
    }
})
