package com.kinetix.risk.model

import java.time.Instant

data class WhatIfResult(
    val baseVaR: Double,
    val baseExpectedShortfall: Double,
    val baseGreeks: GreeksResult?,
    val basePositionRisk: List<PositionRisk>,
    val hypotheticalVaR: Double,
    val hypotheticalExpectedShortfall: Double,
    val hypotheticalGreeks: GreeksResult?,
    val hypotheticalPositionRisk: List<PositionRisk>,
    val varChange: Double,
    val esChange: Double,
    val calculatedAt: Instant,
)
