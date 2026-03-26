package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class LiquidityRiskResponse(
    val bookId: String,
    val var1day: Double,
    val portfolioLvar: Double,
    val lvarRatio: Double,
    val weightedAvgHorizon: Double,
    val maxHorizon: Double,
    val concentrationCount: Int,
    val dataCompleteness: Double,
    val advDataAsOf: String?,
    val portfolioConcentrationStatus: String,
    val calculatedAt: String,
    val positionRisks: List<PositionLiquidityRiskDto>,
)
