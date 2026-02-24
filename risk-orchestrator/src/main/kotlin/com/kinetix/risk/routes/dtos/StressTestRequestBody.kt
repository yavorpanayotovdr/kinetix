package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StressTestRequestBody(
    val scenarioName: String,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val volShocks: Map<String, Double>? = null,
    val priceShocks: Map<String, Double>? = null,
    val description: String? = null,
)
