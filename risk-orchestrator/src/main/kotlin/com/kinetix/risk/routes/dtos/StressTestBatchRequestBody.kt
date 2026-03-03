package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StressTestBatchRequestBody(
    val scenarioNames: List<String>,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
)
