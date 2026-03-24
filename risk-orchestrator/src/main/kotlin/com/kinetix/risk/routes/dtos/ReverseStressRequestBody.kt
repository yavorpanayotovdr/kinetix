package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ReverseStressRequestBody(
    val targetLoss: Double,
    val maxShock: Double = -1.0,
)
