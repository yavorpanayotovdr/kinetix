package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionRiskDto(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: String,
    val delta: String?,
    val gamma: String?,
    val vega: String?,
    val varContribution: String,
    val esContribution: String,
    val percentageOfTotal: String,
)
