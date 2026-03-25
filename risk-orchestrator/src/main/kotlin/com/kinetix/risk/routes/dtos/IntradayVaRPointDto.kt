package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IntradayVaRPointDto(
    val timestamp: String,
    val varValue: Double,
    val expectedShortfall: Double,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
)
