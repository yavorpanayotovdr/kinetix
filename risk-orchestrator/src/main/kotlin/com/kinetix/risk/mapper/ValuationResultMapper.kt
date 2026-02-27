package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.GreeksResult
import com.kinetix.risk.model.ValuationOutput
import com.kinetix.risk.model.ValuationResult
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.risk.ConfidenceLevel as ProtoConfidenceLevel
import com.kinetix.proto.risk.RiskCalculationType
import com.kinetix.proto.risk.ValuationResponse
import com.kinetix.proto.risk.ValuationOutput as ProtoValuationOutput
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

private val PROTO_VALUATION_OUTPUT_TO_DOMAIN = mapOf(
    ProtoValuationOutput.VAR to ValuationOutput.VAR,
    ProtoValuationOutput.EXPECTED_SHORTFALL to ValuationOutput.EXPECTED_SHORTFALL,
    ProtoValuationOutput.GREEKS to ValuationOutput.GREEKS,
    ProtoValuationOutput.PV to ValuationOutput.PV,
)

fun ValuationResponse.toDomainValuation(): ValuationResult {
    val computedOutputs = computedOutputsList
        .mapNotNull { PROTO_VALUATION_OUTPUT_TO_DOMAIN[it] }
        .toSet()

    val greeksResult = if (hasGreeks()) {
        GreeksResult(
            assetClassGreeks = greeks.assetClassGreeksList.map { gv ->
                com.kinetix.risk.model.GreekValues(
                    assetClass = PROTO_ASSET_CLASS_TO_DOMAIN.getValue(gv.assetClass),
                    delta = gv.delta,
                    gamma = gv.gamma,
                    vega = gv.vega,
                )
            },
            theta = greeks.theta,
            rho = greeks.rho,
        )
    } else {
        null
    }

    return ValuationResult(
        portfolioId = PortfolioId(portfolioId.value),
        calculationType = PROTO_CALC_TYPE_TO_DOMAIN.getValue(calculationType),
        confidenceLevel = PROTO_CONFIDENCE_TO_DOMAIN.getValue(confidenceLevel),
        varValue = if (varValue != 0.0) varValue else null,
        expectedShortfall = if (expectedShortfall != 0.0) expectedShortfall else null,
        componentBreakdown = componentBreakdownList.map { cb ->
            ComponentBreakdown(
                assetClass = PROTO_ASSET_CLASS_TO_DOMAIN.getValue(cb.assetClass),
                varContribution = cb.varContribution,
                percentageOfTotal = cb.percentageOfTotal,
            )
        },
        greeks = greeksResult,
        calculatedAt = Instant.ofEpochSecond(calculatedAt.seconds, calculatedAt.nanos.toLong()),
        computedOutputs = computedOutputs,
        pvValue = if (pvValue != 0.0) pvValue else null,
    )
}
