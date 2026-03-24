package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class LiquidityRiskResponse(
    val bookId: String,
    val portfolioLvar: Double,
    val dataCompleteness: Double,
    val portfolioConcentrationStatus: String,
    val calculatedAt: String,
    val positionRisks: List<PositionLiquidityRiskDto>,
)
