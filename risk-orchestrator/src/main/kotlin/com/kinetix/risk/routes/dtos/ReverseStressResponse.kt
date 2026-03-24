package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentShockDto(
    val instrumentId: String,
    val shock: String,
)

@Serializable
data class ReverseStressResponse(
    val shocks: List<InstrumentShockDto>,
    val achievedLoss: String,
    val targetLoss: String,
    val converged: Boolean,
    val calculatedAt: String,
)
