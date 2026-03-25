package com.kinetix.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
data class VolSurfaceResponse(
    val instrumentId: String,
    val asOfDate: String,
    val source: String,
    val points: List<VolPointResponse>,
)
