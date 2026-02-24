package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GreekValuesDto(
    val assetClass: String,
    val delta: String,
    val gamma: String,
    val vega: String,
)
