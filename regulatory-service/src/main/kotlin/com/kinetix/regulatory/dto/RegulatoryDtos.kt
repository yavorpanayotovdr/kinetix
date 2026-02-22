package com.kinetix.regulatory.dto

import kotlinx.serialization.Serializable

@Serializable
data class RiskClassChargeDto(
    val riskClass: String,
    val deltaCharge: String,
    val vegaCharge: String,
    val curvatureCharge: String,
    val totalCharge: String,
)

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

@Serializable
data class FrtbCalculationResponse(
    val id: String,
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
    val storedAt: String,
)

@Serializable
data class FrtbHistoryResponse(
    val calculations: List<FrtbCalculationResponse>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)
