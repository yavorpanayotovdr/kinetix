package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionDiffResponse(
    val instrumentId: String,
    val assetClass: String,
    val changeType: String,
    val baseMarketValue: String,
    val targetMarketValue: String,
    val marketValueChange: String,
    val baseVarContribution: String,
    val targetVarContribution: String,
    val varContributionChange: String,
    val baseDelta: String?,
    val targetDelta: String?,
    val baseGamma: String?,
    val targetGamma: String?,
    val baseVega: String?,
    val targetVega: String?,
)
