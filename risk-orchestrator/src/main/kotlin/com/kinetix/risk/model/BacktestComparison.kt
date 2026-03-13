package com.kinetix.risk.model

data class BacktestComparison(
    val baseConfig: BacktestConfig,
    val targetConfig: BacktestConfig,
    val baseViolationCount: Int,
    val targetViolationCount: Int,
    val violationCountDiff: Int,
    val baseViolationRate: Double,
    val targetViolationRate: Double,
    val violationRateDiff: Double,
    val baseKupiecPValue: Double,
    val targetKupiecPValue: Double,
    val baseChristoffersenPValue: Double,
    val targetChristoffersenPValue: Double,
    val baseTrafficLightZone: String,
    val targetTrafficLightZone: String,
    val trafficLightChanged: Boolean,
)

data class BacktestConfig(
    val calculationType: String,
    val confidenceLevel: String,
    val totalDays: Int,
)
