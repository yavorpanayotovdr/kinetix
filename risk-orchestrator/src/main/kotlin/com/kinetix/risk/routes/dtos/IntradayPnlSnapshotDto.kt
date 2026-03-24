package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IntradayPnlSnapshotDto(
    val snapshotAt: String,
    val baseCurrency: String,
    val trigger: String,
    val totalPnl: String,
    val realisedPnl: String,
    val unrealisedPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
    val highWaterMark: String,
    val instrumentPnl: List<InstrumentPnlBreakdownDto> = emptyList(),
    val correlationId: String? = null,
)
