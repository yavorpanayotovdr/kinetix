package com.kinetix.risk.model

import java.time.Instant

data class TimeSeriesPoint(
    val timestamp: Instant,
    val value: Double,
)
