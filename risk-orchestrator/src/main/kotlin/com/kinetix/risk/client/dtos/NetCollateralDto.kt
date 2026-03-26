package com.kinetix.risk.client.dtos

import kotlinx.serialization.Serializable

@Serializable
data class NetCollateralDto(
    val collateralReceived: Double,
    val collateralPosted: Double,
)
