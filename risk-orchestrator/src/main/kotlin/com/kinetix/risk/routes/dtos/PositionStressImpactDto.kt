package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionStressImpactDto(
    val instrumentId: String,
    val assetClass: String,
    val baseMarketValue: String,
    val stressedMarketValue: String,
    val pnlImpact: String,
    val percentageOfTotal: String,
)
