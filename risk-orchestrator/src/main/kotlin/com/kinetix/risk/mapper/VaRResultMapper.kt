package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRResult
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.risk.ConfidenceLevel as ProtoConfidenceLevel
import com.kinetix.proto.risk.RiskCalculationType
import com.kinetix.proto.risk.VaRResponse
import java.time.Instant

private val PROTO_CALC_TYPE_TO_DOMAIN = mapOf(
    RiskCalculationType.HISTORICAL to CalculationType.HISTORICAL,
    RiskCalculationType.PARAMETRIC to CalculationType.PARAMETRIC,
    RiskCalculationType.MONTE_CARLO to CalculationType.MONTE_CARLO,
)

private val PROTO_CONFIDENCE_TO_DOMAIN = mapOf(
    ProtoConfidenceLevel.CL_95 to ConfidenceLevel.CL_95,
    ProtoConfidenceLevel.CL_99 to ConfidenceLevel.CL_99,
)

private val PROTO_ASSET_CLASS_TO_DOMAIN = mapOf(
    ProtoAssetClass.EQUITY to AssetClass.EQUITY,
    ProtoAssetClass.FIXED_INCOME to AssetClass.FIXED_INCOME,
    ProtoAssetClass.FX to AssetClass.FX,
    ProtoAssetClass.COMMODITY to AssetClass.COMMODITY,
    ProtoAssetClass.DERIVATIVE to AssetClass.DERIVATIVE,
)

private val DOMAIN_CALC_TYPE_TO_PROTO = PROTO_CALC_TYPE_TO_DOMAIN.entries.associate { (k, v) -> v to k }
private val DOMAIN_CONFIDENCE_TO_PROTO = PROTO_CONFIDENCE_TO_DOMAIN.entries.associate { (k, v) -> v to k }

fun VaRResponse.toDomain(): VaRResult = VaRResult(
    portfolioId = PortfolioId(portfolioId.value),
    calculationType = PROTO_CALC_TYPE_TO_DOMAIN.getValue(calculationType),
    confidenceLevel = PROTO_CONFIDENCE_TO_DOMAIN.getValue(confidenceLevel),
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    componentBreakdown = componentBreakdownList.map { cb ->
        ComponentBreakdown(
            assetClass = PROTO_ASSET_CLASS_TO_DOMAIN.getValue(cb.assetClass),
            varContribution = cb.varContribution,
            percentageOfTotal = cb.percentageOfTotal,
        )
    },
    calculatedAt = Instant.ofEpochSecond(calculatedAt.seconds, calculatedAt.nanos.toLong()),
)

fun CalculationType.toProto(): RiskCalculationType = DOMAIN_CALC_TYPE_TO_PROTO.getValue(this)

fun ConfidenceLevel.toProto(): ProtoConfidenceLevel = DOMAIN_CONFIDENCE_TO_PROTO.getValue(this)
