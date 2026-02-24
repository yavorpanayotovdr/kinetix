package com.kinetix.risk.mapper

import com.kinetix.risk.model.CalculationRun
import com.kinetix.risk.model.PipelineStep
import com.kinetix.risk.routes.dtos.CalculationRunDetailResponse
import com.kinetix.risk.routes.dtos.CalculationRunSummaryResponse
import com.kinetix.risk.routes.dtos.PipelineStepResponse

fun CalculationRun.toSummaryResponse(): CalculationRunSummaryResponse = CalculationRunSummaryResponse(
    runId = runId.toString(),
    portfolioId = portfolioId,
    triggerType = triggerType.name,
    status = status.name,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    calculationType = calculationType,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
)

fun CalculationRun.toDetailResponse(): CalculationRunDetailResponse = CalculationRunDetailResponse(
    runId = runId.toString(),
    portfolioId = portfolioId,
    triggerType = triggerType.name,
    status = status.name,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    steps = steps.map { it.toResponse() },
    error = error,
)

private fun PipelineStep.toResponse(): PipelineStepResponse = PipelineStepResponse(
    name = name.name,
    status = status.name,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    details = details.mapValues { (_, v) -> v?.toString() ?: "" },
    error = error,
)
