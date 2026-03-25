package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class BatchScenarioResultDto(
    val scenarioName: String,
    val baseVar: String,
    val stressedVar: String,
    val pnlImpact: String,
)

@Serializable
data class BatchScenarioFailureDto(
    val scenarioName: String,
    val errorMessage: String,
)

@Serializable
data class BatchStressRunResultResponse(
    val results: List<BatchScenarioResultDto>,
    val failedScenarios: List<BatchScenarioFailureDto>,
    val worstScenarioName: String?,
    val worstPnlImpact: String?,
)
