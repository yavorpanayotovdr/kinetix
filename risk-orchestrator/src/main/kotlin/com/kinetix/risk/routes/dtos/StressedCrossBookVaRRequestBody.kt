package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StressedCrossBookVaRRequestBody(
    val bookIds: List<String>,
    val portfolioGroupId: String,
    val stressCorrelation: Double? = null,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
    val monteCarloSeed: String? = null,
)
