package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PnlAttributionResponse(
    val portfolioId: String,
    val date: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
    val positionAttributions: List<PositionPnlAttributionDto>,
    val calculatedAt: String,
)
