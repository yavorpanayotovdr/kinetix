package com.kinetix.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
data class VolPointResponse(
    val strike: Double,
    val maturityDays: Int,
    val impliedVol: Double,
)
