package com.kinetix.regulatory.dto

import kotlinx.serialization.Serializable

@Serializable
data class BacktestComparisonResponse(
    val baseCalculationType: String,
    val baseConfidenceLevel: String,
    val baseTotalDays: Int,
    val baseViolationCount: Int,
    val baseViolationRate: String,
    val baseKupiecPValue: String,
    val baseChristoffersenPValue: String,
    val baseTrafficLightZone: String,
    val targetCalculationType: String,
    val targetConfidenceLevel: String,
    val targetTotalDays: Int,
    val targetViolationCount: Int,
    val targetViolationRate: String,
    val targetKupiecPValue: String,
    val targetChristoffersenPValue: String,
    val targetTrafficLightZone: String,
    val trafficLightChanged: Boolean,
)
