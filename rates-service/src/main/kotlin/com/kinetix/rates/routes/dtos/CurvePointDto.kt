package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CurvePointDto(
    val tenor: String,
    val value: String,
)
