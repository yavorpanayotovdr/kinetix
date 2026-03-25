package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReverseStressRequest(
    val bookId: String,
    val targetLoss: Double,
    val maxShock: Double = -1.0,
)
