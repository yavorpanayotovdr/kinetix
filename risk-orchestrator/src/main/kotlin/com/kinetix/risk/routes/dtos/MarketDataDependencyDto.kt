package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class MarketDataDependencyDto(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val required: Boolean,
    val description: String,
    val parameters: Map<String, String>,
)
