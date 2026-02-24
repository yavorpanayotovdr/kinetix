package com.kinetix.risk.model

data class ScalarMarketData(
    override val dataType: String,
    override val instrumentId: String,
    override val assetClass: String,
    val value: Double,
) : MarketDataValue
