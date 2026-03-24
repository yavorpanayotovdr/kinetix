package com.kinetix.risk.client.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentLiquidityDto(
    val instrumentId: String,
    val adv: Double,
    val bidAskSpreadBps: Double,
    val assetClass: String,
    val advUpdatedAt: String,
    val advStale: Boolean,
    val advStalenessDays: Int,
    val createdAt: String,
    val updatedAt: String,
)
