package com.kinetix.volatility.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestVolSurfaceRequest(
    val instrumentId: String,
    val points: List<VolPointDto>,
    val source: String,
)
