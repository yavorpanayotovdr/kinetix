package com.kinetix.regulatory.dto

import kotlinx.serialization.Serializable

@Serializable
data class BacktestRequest(
    val dailyVarPredictions: List<Double>,
    val dailyPnl: List<Double>,
    val confidenceLevel: Double = 0.99,
    val calculationType: String = "PARAMETRIC",
)

@Serializable
data class BacktestResultResponse(
    val id: String,
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val totalDays: Int,
    val violationCount: Int,
    val violationRate: String,
    val kupiecStatistic: String,
    val kupiecPValue: String,
    val kupiecPass: Boolean,
    val christoffersenStatistic: String,
    val christoffersenPValue: String,
    val christoffersenPass: Boolean,
    val trafficLightZone: String,
    val calculatedAt: String,
)

@Serializable
data class BacktestHistoryResponse(
    val results: List<BacktestResultResponse>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)
