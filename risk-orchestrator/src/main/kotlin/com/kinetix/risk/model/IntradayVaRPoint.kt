package com.kinetix.risk.model

import java.time.Instant

data class IntradayVaRPoint(
    val timestamp: Instant,
    val varValue: Double,
    val expectedShortfall: Double,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
)
