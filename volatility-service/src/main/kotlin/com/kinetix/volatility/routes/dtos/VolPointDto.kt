package com.kinetix.volatility.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VolPointDto(
    val strike: Double,
    val maturityDays: Int,
    val impliedVol: Double,
)
