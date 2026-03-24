package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionLiquidityRiskDto(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: Double,
    val tier: String,
    val horizonDays: Int,
    val adv: Double?,
    val advPct: Double?,
    val advMissing: Boolean,
    val advStale: Boolean,
    val lvarContribution: Double,
    val stressedLiquidationValue: Double,
    val concentrationStatus: String,
)
