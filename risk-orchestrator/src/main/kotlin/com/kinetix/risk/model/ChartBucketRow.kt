package com.kinetix.risk.model

import java.time.Instant

data class ChartBucketRow(
    val bucket: Instant,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val confidenceLevel: String? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val vega: Double? = null,
    val theta: Double? = null,
    val rho: Double? = null,
    val pvValue: Double? = null,
    val jobCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val runningCount: Int,
)
