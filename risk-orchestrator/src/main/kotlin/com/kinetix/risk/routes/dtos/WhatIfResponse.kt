package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class WhatIfResponse(
    val baseVaR: String,
    val baseExpectedShortfall: String,
    val baseGreeks: GreeksResponse?,
    val basePositionRisk: List<PositionRiskDto>,
    val hypotheticalVaR: String,
    val hypotheticalExpectedShortfall: String,
    val hypotheticalGreeks: GreeksResponse?,
    val hypotheticalPositionRisk: List<PositionRiskDto>,
    val varChange: String,
    val esChange: String,
    val calculatedAt: String,
)
