package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class MarketDataInputChangeDto(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val changeType: String,
    val magnitude: String? = null,
)
