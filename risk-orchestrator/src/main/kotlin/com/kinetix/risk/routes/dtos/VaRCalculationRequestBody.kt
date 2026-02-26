package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VaRCalculationRequestBody(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
    val requestedOutputs: List<String>? = null,
)
