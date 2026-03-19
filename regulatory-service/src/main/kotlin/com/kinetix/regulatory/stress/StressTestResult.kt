package com.kinetix.regulatory.stress

import java.math.BigDecimal
import java.time.Instant

data class StressTestResult(
    val id: String,
    val scenarioId: String,
    val bookId: String,
    val calculatedAt: Instant,
    val basePv: BigDecimal?,
    val stressedPv: BigDecimal?,
    val pnlImpact: BigDecimal?,
    val varImpact: Double?,
    val positionImpacts: String?,
    val modelVersion: String?,
)
