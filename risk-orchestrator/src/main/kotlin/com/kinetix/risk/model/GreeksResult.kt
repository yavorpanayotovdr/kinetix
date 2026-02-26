package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass

data class GreekValues(
    val assetClass: AssetClass,
    val delta: Double,
    val gamma: Double,
    val vega: Double,
)

data class GreeksResult(
    val assetClassGreeks: List<GreekValues>,
    val theta: Double,
    val rho: Double,
)
