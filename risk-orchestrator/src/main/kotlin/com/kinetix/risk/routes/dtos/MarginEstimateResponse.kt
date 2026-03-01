package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class MarginEstimateResponse(
    val initialMargin: String,
    val variationMargin: String,
    val totalMargin: String,
    val currency: String,
)
