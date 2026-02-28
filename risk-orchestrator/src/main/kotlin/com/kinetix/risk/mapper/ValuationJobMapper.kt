package com.kinetix.risk.mapper

import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.model.JobStep
import com.kinetix.risk.routes.dtos.ValuationJobDetailResponse
import com.kinetix.risk.routes.dtos.ValuationJobSummaryResponse
import com.kinetix.risk.routes.dtos.JobStepResponse

fun ValuationJob.toSummaryResponse(): ValuationJobSummaryResponse = ValuationJobSummaryResponse(
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
    pvValue = pvValue,
)

fun ValuationJob.toDetailResponse(): ValuationJobDetailResponse = ValuationJobDetailResponse(
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
    pvValue = pvValue,
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
