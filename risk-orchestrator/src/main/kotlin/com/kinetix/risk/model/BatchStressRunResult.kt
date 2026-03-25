package com.kinetix.risk.model

data class BatchScenarioFailure(
    val scenarioName: String,
    val errorMessage: String,
)

data class BatchStressRunResult(
    val results: List<BatchScenarioResult>,
    val failedScenarios: List<BatchScenarioFailure>,
    val worstScenarioName: String?,
    val worstPnlImpact: String?,
)

data class BatchScenarioResult(
    val scenarioName: String,
    val baseVar: String,
    val stressedVar: String,
    val pnlImpact: String,
)
