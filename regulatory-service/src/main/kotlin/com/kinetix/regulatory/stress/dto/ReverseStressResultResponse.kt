package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentShock(
    val instrumentId: String,
    val shock: String,
)

@Serializable
data class ReverseStressResultResponse(
    val shocks: List<InstrumentShock>,
    val achievedLoss: String,
    val targetLoss: String,
    val converged: Boolean,
    val calculatedAt: String,
)
