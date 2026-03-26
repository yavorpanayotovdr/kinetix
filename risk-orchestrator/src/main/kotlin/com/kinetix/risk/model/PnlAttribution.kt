package com.kinetix.risk.model

import com.kinetix.common.model.BookId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PnlAttribution(
    val bookId: BookId,
    val date: LocalDate,
    val currency: String,
    val totalPnl: BigDecimal,
    // First-order Greek attribution
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
    val positionAttributions: List<PositionPnlAttribution>,
    val dataQualityFlag: AttributionDataQuality = AttributionDataQuality.PRICE_ONLY,
    val calculatedAt: Instant,
)
