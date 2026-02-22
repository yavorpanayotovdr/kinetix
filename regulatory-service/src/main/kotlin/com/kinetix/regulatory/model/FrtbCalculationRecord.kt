package com.kinetix.regulatory.model

import java.time.Instant

data class RiskClassCharge(
    val riskClass: String,
    val deltaCharge: Double,
    val vegaCharge: Double,
    val curvatureCharge: Double,
    val totalCharge: Double,
)

data class FrtbCalculationRecord(
    val id: String,
    val portfolioId: String,
    val totalSbmCharge: Double,
    val grossJtd: Double,
    val hedgeBenefit: Double,
    val netDrc: Double,
    val exoticNotional: Double,
    val otherNotional: Double,
    val totalRrao: Double,
    val totalCapitalCharge: Double,
    val sbmCharges: List<RiskClassCharge>,
    val calculatedAt: Instant,
    val storedAt: Instant,
)
