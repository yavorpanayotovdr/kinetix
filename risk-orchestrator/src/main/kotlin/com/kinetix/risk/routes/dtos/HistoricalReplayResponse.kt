package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionReplayImpactDto(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: String,
    val pnlImpact: String,
    val dailyPnl: List<String>,
    val proxyUsed: Boolean,
)

@Serializable
data class HistoricalReplayResponse(
    val scenarioName: String,
    val totalPnlImpact: String,
    val positionImpacts: List<PositionReplayImpactDto>,
    val windowStart: String?,
    val windowEnd: String?,
    val calculatedAt: String,
)
