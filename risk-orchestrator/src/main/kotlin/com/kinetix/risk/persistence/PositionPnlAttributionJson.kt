package com.kinetix.risk.persistence

import kotlinx.serialization.Serializable

@Serializable
data class PositionPnlAttributionJson(
    val instrumentId: String,
    val assetClass: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val vannaPnl: String = "0",
    val volgaPnl: String = "0",
    val charmPnl: String = "0",
    val crossGammaPnl: String = "0",
    val unexplainedPnl: String,
)
