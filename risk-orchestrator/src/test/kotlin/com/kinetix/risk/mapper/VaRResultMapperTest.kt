package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.risk.ConfidenceLevel as ProtoConfidenceLevel
import com.kinetix.proto.risk.RiskCalculationType
import com.kinetix.proto.risk.VaRComponentBreakdown
import com.kinetix.proto.risk.VaRResponse
import com.google.protobuf.Timestamp

class VaRResultMapperTest : FunSpec({

    test("maps proto VaRResponse to domain VaRResult") {
        val now = Timestamp.newBuilder().setSeconds(1700000000).build()
        val response = VaRResponse.newBuilder()
            .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("port-1"))
            .setCalculationType(RiskCalculationType.PARAMETRIC)
            .setConfidenceLevel(ProtoConfidenceLevel.CL_95)
            .setVarValue(25000.0)
            .setExpectedShortfall(31000.0)
            .addComponentBreakdown(
                VaRComponentBreakdown.newBuilder()
                    .setAssetClass(ProtoAssetClass.EQUITY)
                    .setVarContribution(15000.0)
                    .setPercentageOfTotal(60.0)
            )
            .addComponentBreakdown(
                VaRComponentBreakdown.newBuilder()
                    .setAssetClass(ProtoAssetClass.FIXED_INCOME)
                    .setVarContribution(10000.0)
                    .setPercentageOfTotal(40.0)
            )
            .setCalculatedAt(now)
            .build()

        val result = response.toDomain()

        result.portfolioId shouldBe PortfolioId("port-1")
        result.calculationType shouldBe CalculationType.PARAMETRIC
        result.confidenceLevel shouldBe ConfidenceLevel.CL_95
        result.varValue shouldBe 25000.0
        result.expectedShortfall shouldBe 31000.0
        result.componentBreakdown shouldHaveSize 2
        result.componentBreakdown[0].assetClass shouldBe AssetClass.EQUITY
        result.componentBreakdown[0].varContribution shouldBe 15000.0
        result.componentBreakdown[0].percentageOfTotal shouldBe 60.0
        result.componentBreakdown[1].assetClass shouldBe AssetClass.FIXED_INCOME
        result.calculatedAt.epochSecond shouldBe 1700000000
    }

    test("maps all calculation types") {
        val calcTypes = mapOf(
            RiskCalculationType.HISTORICAL to CalculationType.HISTORICAL,
            RiskCalculationType.PARAMETRIC to CalculationType.PARAMETRIC,
            RiskCalculationType.MONTE_CARLO to CalculationType.MONTE_CARLO,
        )

        for ((proto, domain) in calcTypes) {
            val response = VaRResponse.newBuilder()
                .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("p"))
                .setCalculationType(proto)
                .setConfidenceLevel(ProtoConfidenceLevel.CL_95)
                .setVarValue(1.0)
                .setExpectedShortfall(2.0)
                .setCalculatedAt(Timestamp.newBuilder().setSeconds(1000))
                .build()
            response.toDomain().calculationType shouldBe domain
        }
    }

    test("maps both confidence levels") {
        val clMappings = mapOf(
            ProtoConfidenceLevel.CL_95 to ConfidenceLevel.CL_95,
            ProtoConfidenceLevel.CL_99 to ConfidenceLevel.CL_99,
        )

        for ((proto, domain) in clMappings) {
            val response = VaRResponse.newBuilder()
                .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("p"))
                .setCalculationType(RiskCalculationType.PARAMETRIC)
                .setConfidenceLevel(proto)
                .setVarValue(1.0)
                .setExpectedShortfall(2.0)
                .setCalculatedAt(Timestamp.newBuilder().setSeconds(1000))
                .build()
            response.toDomain().confidenceLevel shouldBe domain
        }
    }

    test("maps all proto asset classes to domain") {
        val acMappings = mapOf(
            ProtoAssetClass.EQUITY to AssetClass.EQUITY,
            ProtoAssetClass.FIXED_INCOME to AssetClass.FIXED_INCOME,
            ProtoAssetClass.FX to AssetClass.FX,
            ProtoAssetClass.COMMODITY to AssetClass.COMMODITY,
            ProtoAssetClass.DERIVATIVE to AssetClass.DERIVATIVE,
        )

        for ((proto, domain) in acMappings) {
            val breakdown = VaRComponentBreakdown.newBuilder()
                .setAssetClass(proto)
                .setVarContribution(100.0)
                .setPercentageOfTotal(100.0)
                .build()
            val response = VaRResponse.newBuilder()
                .setPortfolioId(com.kinetix.proto.common.PortfolioId.newBuilder().setValue("p"))
                .setCalculationType(RiskCalculationType.PARAMETRIC)
                .setConfidenceLevel(ProtoConfidenceLevel.CL_95)
                .setVarValue(100.0)
                .setExpectedShortfall(120.0)
                .addComponentBreakdown(breakdown)
                .setCalculatedAt(Timestamp.newBuilder().setSeconds(1000))
                .build()
            response.toDomain().componentBreakdown[0].assetClass shouldBe domain
        }
    }
})
