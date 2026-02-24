package com.kinetix.risk.model

data class TimeSeriesMarketData(
    override val dataType: String,
    override val instrumentId: String,
    override val assetClass: String,
    val points: List<TimeSeriesPoint>,
) : MarketDataValue
