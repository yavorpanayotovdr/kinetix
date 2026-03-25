package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class StressScenarioResponse(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: String,
    val createdBy: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val createdAt: String,
    val scenarioType: String = "PARAMETRIC",
    val version: Int = 1,
    val parentScenarioId: String? = null,
    val correlationOverride: String? = null,
    val liquidityStressFactors: String? = null,
    val historicalPeriodId: String? = null,
    val targetLoss: String? = null,
)
