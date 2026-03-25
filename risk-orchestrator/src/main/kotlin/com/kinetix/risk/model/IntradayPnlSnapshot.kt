package com.kinetix.risk.model

import com.kinetix.common.model.BookId
import java.math.BigDecimal
import java.time.Instant

data class IntradayPnlSnapshot(
    val id: Long? = null,
    val bookId: BookId,
    val snapshotAt: Instant,
    val baseCurrency: String,
    val trigger: PnlTrigger,

    // Total P&L: the truth, computed from position state
    val totalPnl: BigDecimal,
    val realisedPnl: BigDecimal,
    val unrealisedPnl: BigDecimal,

    // First-order Greek attribution using frozen SOD Greeks
    val deltaPnl: BigDecimal,
    val gammaPnl: BigDecimal,
    val vegaPnl: BigDecimal,
    val thetaPnl: BigDecimal,
    val rhoPnl: BigDecimal,

    // Cross-Greek attribution (second-order mixed terms)
    val vannaPnl: BigDecimal = BigDecimal.ZERO,
    val volgaPnl: BigDecimal = BigDecimal.ZERO,
    val charmPnl: BigDecimal = BigDecimal.ZERO,
    val crossGammaPnl: BigDecimal = BigDecimal.ZERO,

    // Residual: total_pnl minus sum of all attributed terms
    val unexplainedPnl: BigDecimal,

    // High-water mark: monotonically non-decreasing within a trading day
    val highWaterMark: BigDecimal,

    // Per-instrument attribution breakdown
    val instrumentPnl: List<InstrumentPnlBreakdown> = emptyList(),

    val correlationId: String? = null,
)
