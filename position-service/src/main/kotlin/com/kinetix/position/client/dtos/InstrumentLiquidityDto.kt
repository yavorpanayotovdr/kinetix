package com.kinetix.position.client.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentLiquidityDto(
    val instrumentId: String,
    val adv: Double,
)
