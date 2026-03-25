package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PnlAttributionResponse(
    val bookId: String,
    val date: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val vannaPnl: String,
    val volgaPnl: String,
    val charmPnl: String,
    val crossGammaPnl: String,
    val unexplainedPnl: String,
    val positionAttributions: List<PositionPnlAttributionDto>,
    val dataQualityFlag: String,
    val calculatedAt: String,
)
