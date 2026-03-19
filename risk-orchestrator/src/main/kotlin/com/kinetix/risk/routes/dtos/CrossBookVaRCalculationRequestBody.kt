package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CrossBookVaRCalculationRequestBody(
    val bookIds: List<String>,
    val portfolioGroupId: String,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
    val monteCarloSeed: String? = null,
)
