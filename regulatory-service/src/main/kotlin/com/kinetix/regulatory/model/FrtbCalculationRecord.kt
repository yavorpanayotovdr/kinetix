package com.kinetix.regulatory.model

import java.math.BigDecimal
import java.time.Instant

data class RiskClassCharge(
    val riskClass: String,
    val deltaCharge: BigDecimal,
    val vegaCharge: BigDecimal,
    val curvatureCharge: BigDecimal,
    val totalCharge: BigDecimal,
)

data class FrtbCalculationRecord(
    val id: String,
    val portfolioId: String,
    val totalSbmCharge: BigDecimal,
    val grossJtd: BigDecimal,
    val hedgeBenefit: BigDecimal,
    val netDrc: BigDecimal,
    val exoticNotional: BigDecimal,
    val otherNotional: BigDecimal,
    val totalRrao: BigDecimal,
    val totalCapitalCharge: BigDecimal,
    val sbmCharges: List<RiskClassCharge>,
    val calculatedAt: Instant,
    val storedAt: Instant,
)
