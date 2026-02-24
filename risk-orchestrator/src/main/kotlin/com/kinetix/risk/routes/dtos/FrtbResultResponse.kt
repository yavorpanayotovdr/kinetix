package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class FrtbResultResponse(
    val portfolioId: String,
    val sbmCharges: List<RiskClassChargeDto>,
    val totalSbmCharge: String,
    val grossJtd: String,
    val hedgeBenefit: String,
    val netDrc: String,
    val exoticNotional: String,
    val otherNotional: String,
    val totalRrao: String,
    val totalCapitalCharge: String,
    val calculatedAt: String,
)
