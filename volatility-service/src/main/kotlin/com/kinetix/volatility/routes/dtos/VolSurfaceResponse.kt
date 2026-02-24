package com.kinetix.volatility.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VolSurfaceResponse(
    val instrumentId: String,
    val asOfDate: String,
    val points: List<VolPointDto>,
    val source: String,
)
