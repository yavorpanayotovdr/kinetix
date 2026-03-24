package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class UpsertInstrumentLiquidityRequest(
    val instrumentId: String,
    val adv: Double,
    val bidAskSpreadBps: Double = 0.0,
    val assetClass: String,
)
