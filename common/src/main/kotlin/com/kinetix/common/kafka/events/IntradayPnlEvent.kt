package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Canonical Kafka event schema for intraday P&L snapshots on the `risk.pnl.intraday` topic.
 *
 * Published by risk-orchestrator after each debounced P&L recomputation.
 * Consumed by the gateway's PnlBroadcaster to push updates to WebSocket clients.
 *
 * Total P&L is always computed from position state (realised + unrealised) — never
 * derived from Greek attribution. Greek components are an analytical overlay using
 * frozen SOD Greeks. unexplained_pnl absorbs the residual.
 *
 * Invariant: total_pnl == delta_pnl + gamma_pnl + vega_pnl + theta_pnl + rho_pnl + unexplained_pnl
 */
@Serializable
data class IntradayPnlEvent(
    val bookId: String,
    val snapshotAt: String,
    val baseCurrency: String,
    val trigger: String,

    // Total P&L — the truth, from position state
    val totalPnl: String,
    val realisedPnl: String,
    val unrealisedPnl: String,

    // Greek attribution (analytical overlay, uses frozen SOD Greeks)
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,

    // High-water mark: monotonically non-decreasing within a trading day
    val highWaterMark: String,

    // Per-instrument breakdown; null for events published before this field was added
    val instrumentPnl: List<InstrumentPnlItem>? = null,

    val correlationId: String? = null,
)

@Serializable
data class InstrumentPnlItem(
    val instrumentId: String,
    val assetClass: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
)
