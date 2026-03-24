package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class HedgeSuggestRequestBody(
    val targetMetric: String,
    val targetReductionPct: Double,
    val maxSuggestions: Int = 5,
    val maxNotional: Double? = null,
    val respectPositionLimits: Boolean = true,
    val instrumentUniverse: String? = null,
    val allowedSides: List<String>? = null,
)
