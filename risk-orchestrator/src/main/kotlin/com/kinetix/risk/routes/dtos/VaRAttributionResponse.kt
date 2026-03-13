package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VaRAttributionResponse(
    val totalChange: String,
    val positionEffect: String,
    val volEffect: String,
    val corrEffect: String,
    val timeDecayEffect: String,
    val unexplained: String,
)
