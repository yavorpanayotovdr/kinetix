package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateScenarioRequest(
    val name: String,
    val description: String,
    val shocks: String,
    val createdBy: String,
    val scenarioType: String = "PARAMETRIC",
    val parentScenarioId: String? = null,
    val correlationOverride: String? = null,
    val liquidityStressFactors: String? = null,
    val historicalPeriodId: String? = null,
    val targetLoss: String? = null,
)
