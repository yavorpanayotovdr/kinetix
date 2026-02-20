package com.kinetix.risk.client

import com.kinetix.common.model.*
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub
import com.kinetix.proto.risk.RiskCalculationType
import com.kinetix.proto.risk.VaRComponentBreakdown
import com.kinetix.proto.risk.VaRResponse
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.google.protobuf.Timestamp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import java.math.BigDecimal
import java.util.Currency
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.risk.ConfidenceLevel as ProtoConfidenceLevel

private val USD = Currency.getInstance("USD")

class GrpcRiskEngineClientTest : FunSpec({

    val stub = mockk<RiskCalculationServiceCoroutineStub>()
    val client = GrpcRiskEngineClient(stub)

    test("maps request to proto and invokes stub") {
        val positions = listOf(
            Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), USD),
                marketPrice = Money(BigDecimal("170.00"), USD),
            ),
        )
        val request = VaRCalculationRequest(
            portfolioId = PortfolioId("port-1"),
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
            timeHorizonDays = 1,
            numSimulations = 10_000,
        )

        val protoResponse = VaRResponse.newBuilder()
            .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("port-1"))
            .setCalculationType(RiskCalculationType.PARAMETRIC)
            .setConfidenceLevel(ProtoConfidenceLevel.CL_95)
            .setVarValue(5000.0)
            .setExpectedShortfall(6200.0)
            .addComponentBreakdown(
                VaRComponentBreakdown.newBuilder()
                    .setAssetClass(ProtoAssetClass.EQUITY)
                    .setVarContribution(5000.0)
                    .setPercentageOfTotal(100.0)
            )
            .setCalculatedAt(Timestamp.newBuilder().setSeconds(1700000000))
            .build()

        coEvery { stub.calculateVaR(any(), any()) } returns protoResponse

        val result = client.calculateVaR(request, positions)

        result.portfolioId shouldBe PortfolioId("port-1")
        result.calculationType shouldBe CalculationType.PARAMETRIC
        result.confidenceLevel shouldBe ConfidenceLevel.CL_95
        result.varValue shouldBe 5000.0
        result.expectedShortfall shouldBe 6200.0
        result.componentBreakdown shouldHaveSize 1
        result.componentBreakdown[0].assetClass shouldBe AssetClass.EQUITY

        coVerify {
            stub.calculateVaR(match { req ->
                req.portfolioId.value == "port-1" &&
                    req.calculationType == RiskCalculationType.PARAMETRIC &&
                    req.confidenceLevel == ProtoConfidenceLevel.CL_95 &&
                    req.timeHorizonDays == 1 &&
                    req.numSimulations == 10_000 &&
                    req.positionsCount == 1
            }, any())
        }
    }

    test("passes multiple positions in proto request") {
        val positions = listOf(
            Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), USD),
                marketPrice = Money(BigDecimal("170.00"), USD),
            ),
            Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("UST10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                quantity = BigDecimal("50"),
                averageCost = Money(BigDecimal("9800.00"), USD),
                marketPrice = Money(BigDecimal("10000.00"), USD),
            ),
        )
        val request = VaRCalculationRequest(
            portfolioId = PortfolioId("port-1"),
            calculationType = CalculationType.HISTORICAL,
            confidenceLevel = ConfidenceLevel.CL_99,
        )

        val protoResponse = VaRResponse.newBuilder()
            .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("port-1"))
            .setCalculationType(RiskCalculationType.HISTORICAL)
            .setConfidenceLevel(ProtoConfidenceLevel.CL_99)
            .setVarValue(12000.0)
            .setExpectedShortfall(15000.0)
            .setCalculatedAt(Timestamp.newBuilder().setSeconds(1700000000))
            .build()

        coEvery { stub.calculateVaR(any(), any()) } returns protoResponse

        val result = client.calculateVaR(request, positions)

        result.varValue shouldBe 12000.0

        coVerify {
            stub.calculateVaR(match { req -> req.positionsCount == 2 }, any())
        }
    }
})
