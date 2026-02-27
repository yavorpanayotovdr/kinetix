package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.risk.model.ValuationResult
import com.kinetix.risk.routes.dtos.ComponentBreakdownDto
import com.kinetix.risk.routes.dtos.GreekValuesDto
import com.kinetix.risk.routes.dtos.GreeksResponse
import com.kinetix.risk.routes.dtos.VaRResultResponse
import com.kinetix.proto.risk.FrtbRiskClass
import com.kinetix.proto.risk.MarketDataType

internal fun ValuationResult.toResponse() = VaRResultResponse(
    portfolioId = portfolioId.value,
    calculationType = calculationType.name,
    confidenceLevel = confidenceLevel.name,
    varValue = "%.2f".format(varValue ?: 0.0),
    expectedShortfall = "%.2f".format(expectedShortfall ?: 0.0),
    componentBreakdown = componentBreakdown.map {
        ComponentBreakdownDto(
            assetClass = it.assetClass.name,
            varContribution = "%.2f".format(it.varContribution),
            percentageOfTotal = "%.2f".format(it.percentageOfTotal),
        )
    },
    calculatedAt = calculatedAt.toString(),
    greeks = greeks?.let { g ->
        GreeksResponse(
            portfolioId = portfolioId.value,
            assetClassGreeks = g.assetClassGreeks.map { gv ->
                GreekValuesDto(
                    assetClass = gv.assetClass.name,
                    delta = "%.6f".format(gv.delta),
                    gamma = "%.6f".format(gv.gamma),
                    vega = "%.6f".format(gv.vega),
                )
            },
            theta = "%.6f".format(g.theta),
            rho = "%.6f".format(g.rho),
            calculatedAt = calculatedAt.toString(),
        )
    },
    computedOutputs = computedOutputs.map { it.name },
    pvValue = pvValue?.let { "%.2f".format(it) },
)

internal val FRTB_RISK_CLASS_NAMES = mapOf(
    FrtbRiskClass.GIRR to "GIRR",
    FrtbRiskClass.CSR_NON_SEC to "CSR_NON_SEC",
    FrtbRiskClass.CSR_SEC_CTP to "CSR_SEC_CTP",
    FrtbRiskClass.CSR_SEC_NON_CTP to "CSR_SEC_NON_CTP",
    FrtbRiskClass.FRTB_EQUITY to "EQUITY",
    FrtbRiskClass.FRTB_COMMODITY to "COMMODITY",
    FrtbRiskClass.FRTB_FX to "FX",
)

internal val ASSET_CLASS_TO_PROTO = mapOf(
    AssetClass.EQUITY to com.kinetix.proto.common.AssetClass.EQUITY,
    AssetClass.FIXED_INCOME to com.kinetix.proto.common.AssetClass.FIXED_INCOME,
    AssetClass.FX to com.kinetix.proto.common.AssetClass.FX,
    AssetClass.COMMODITY to com.kinetix.proto.common.AssetClass.COMMODITY,
    AssetClass.DERIVATIVE to com.kinetix.proto.common.AssetClass.DERIVATIVE,
)

internal val PROTO_ASSET_CLASS_TO_DOMAIN = ASSET_CLASS_TO_PROTO.entries.associate { (k, v) -> v to k }

internal val MARKET_DATA_TYPE_NAMES = mapOf(
    MarketDataType.SPOT_PRICE to "SPOT_PRICE",
    MarketDataType.HISTORICAL_PRICES to "HISTORICAL_PRICES",
    MarketDataType.VOLATILITY_SURFACE to "VOLATILITY_SURFACE",
    MarketDataType.YIELD_CURVE to "YIELD_CURVE",
    MarketDataType.RISK_FREE_RATE to "RISK_FREE_RATE",
    MarketDataType.DIVIDEND_YIELD to "DIVIDEND_YIELD",
    MarketDataType.CREDIT_SPREAD to "CREDIT_SPREAD",
    MarketDataType.FORWARD_CURVE to "FORWARD_CURVE",
    MarketDataType.CORRELATION_MATRIX to "CORRELATION_MATRIX",
)
