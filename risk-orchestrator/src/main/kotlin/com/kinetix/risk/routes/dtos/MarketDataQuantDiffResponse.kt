package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class MarketDataQuantDiffResponse(
    val dataType: String,
    val instrumentId: String,
    val magnitude: String,
    val summary: String? = null,
    val caveats: List<String> = emptyList(),
    val diagnostic: Boolean = true,
)
