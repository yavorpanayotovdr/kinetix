package com.kinetix.regulatory.model

import java.time.Instant
import java.time.LocalDate

data class BacktestResultRecord(
    val id: String,
    val bookId: String,
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
    val inputDigest: String? = null,
    val windowStart: LocalDate? = null,
    val windowEnd: LocalDate? = null,
    val modelVersion: String? = null,
)
