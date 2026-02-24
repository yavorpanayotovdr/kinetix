package com.kinetix.risk.model

data class MatrixMarketData(
    override val dataType: String,
    override val instrumentId: String,
    override val assetClass: String,
    val rows: List<String>,
    val columns: List<String>,
    val values: List<Double>,
) : MarketDataValue
