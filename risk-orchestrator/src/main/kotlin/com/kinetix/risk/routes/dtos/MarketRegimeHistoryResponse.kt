package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class MarketRegimeHistoryItemResponse(
    val id: String,
    val regime: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationMs: Long? = null,
    val confidence: Double,
    val consecutiveObservations: Int,
    val degradedInputs: Boolean,
    val signals: RegimeSignalsDto,
    val varParameters: AdaptiveVaRParametersDto,
)

@Serializable
data class MarketRegimeHistoryResponse(
    val items: List<MarketRegimeHistoryItemResponse>,
    val total: Int,
)
