package com.kinetix.risk.mapper

import com.kinetix.risk.model.CalculationJob
import com.kinetix.risk.model.JobStep
import com.kinetix.risk.routes.dtos.CalculationJobDetailResponse
import com.kinetix.risk.routes.dtos.CalculationJobSummaryResponse
import com.kinetix.risk.routes.dtos.JobStepResponse

fun CalculationJob.toSummaryResponse(): CalculationJobSummaryResponse = CalculationJobSummaryResponse(
    jobId = jobId.toString(),
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

fun CalculationJob.toDetailResponse(): CalculationJobDetailResponse = CalculationJobDetailResponse(
    jobId = jobId.toString(),
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

private fun JobStep.toResponse(): JobStepResponse = JobStepResponse(
    name = name.name,
    status = status.name,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    details = details.mapValues { (_, v) -> v?.toString() ?: "" },
    error = error,
)
