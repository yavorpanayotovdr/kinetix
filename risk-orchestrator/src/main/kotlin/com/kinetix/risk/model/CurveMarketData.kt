package com.kinetix.risk.model

data class CurveMarketData(
    override val dataType: String,
    override val instrumentId: String,
    override val assetClass: String,
    val points: List<CurvePointValue>,
) : MarketDataValue
