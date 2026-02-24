package com.kinetix.risk.model

sealed interface MarketDataValue {
    val dataType: String
    val instrumentId: String
    val assetClass: String
}
