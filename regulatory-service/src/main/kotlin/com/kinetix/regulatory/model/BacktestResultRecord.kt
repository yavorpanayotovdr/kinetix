package com.kinetix.regulatory.model

import java.time.Instant

data class BacktestResultRecord(
    val id: String,
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: Double,
    val totalDays: Int,
    val violationCount: Int,
    val violationRate: Double,
    val kupiecStatistic: Double,
    val kupiecPValue: Double,
    val kupiecPass: Boolean,
    val christoffersenStatistic: Double,
    val christoffersenPValue: Double,
    val christoffersenPass: Boolean,
    val trafficLightZone: String,
    val calculatedAt: Instant,
)
