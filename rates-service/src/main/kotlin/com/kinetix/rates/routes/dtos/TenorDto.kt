package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class TenorDto(
    val label: String,
    val days: Int,
    val rate: String,
)
