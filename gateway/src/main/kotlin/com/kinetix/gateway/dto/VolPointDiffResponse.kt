package com.kinetix.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
data class VolPointDiffResponse(
    val strike: Double,
    val maturityDays: Int,
    val baseVol: Double,
    val compareVol: Double,
    val diff: Double,
)
