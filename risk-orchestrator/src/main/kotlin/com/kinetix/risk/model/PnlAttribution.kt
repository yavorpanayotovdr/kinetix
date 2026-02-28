package com.kinetix.risk.model

import com.kinetix.common.model.PortfolioId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PnlAttribution(
    val portfolioId: PortfolioId,
    val date: LocalDate,
    val totalPnl: BigDecimal,
    val deltaPnl: BigDecimal,
    val gammaPnl: BigDecimal,
    val vegaPnl: BigDecimal,
    val thetaPnl: BigDecimal,
    val rhoPnl: BigDecimal,
    val unexplainedPnl: BigDecimal,
    val positionAttributions: List<PositionPnlAttribution>,
    val calculatedAt: Instant,
)
