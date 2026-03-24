package com.kinetix.referencedata.model

import java.time.Instant

data class InstrumentLiquidity(
    val instrumentId: String,
    val adv: Double,
    val bidAskSpreadBps: Double,
    val assetClass: String,
    val liquidityTier: InstrumentLiquidityTier,
    val advUpdatedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
