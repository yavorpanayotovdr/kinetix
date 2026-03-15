package com.kinetix.risk.mapper

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import com.kinetix.risk.routes.dtos.ValuationJobDetailResponse
import com.kinetix.risk.routes.dtos.ValuationJobSummaryResponse
import com.kinetix.risk.routes.dtos.JobPhaseResponse

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
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    valuationDate = valuationDate.toString(),
    runLabel = runLabel?.name,
    promotedAt = promotedAt?.toString(),
    promotedBy = promotedBy,
    currentPhase = currentPhase?.name,
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
    phases = phases.map { it.toResponse() },
    error = error,
    valuationDate = valuationDate.toString(),
    runLabel = runLabel?.name,
    promotedAt = promotedAt?.toString(),
    promotedBy = promotedBy,
    currentPhase = currentPhase?.name,
)

fun ValuationJob.toValuationResult(): ValuationResult? {
    if (status != RunStatus.COMPLETED) return null
    if (calculationType == null || confidenceLevel == null) return null
    return ValuationResult(
        portfolioId = PortfolioId(portfolioId),
        calculationType = CalculationType.valueOf(calculationType),
        confidenceLevel = ConfidenceLevel.valueOf(confidenceLevel),
        varValue = varValue,
        expectedShortfall = expectedShortfall,
        componentBreakdown = componentBreakdownSnapshot,
        greeks = assetClassGreeksSnapshot.takeIf { it.isNotEmpty() }?.let { acGreeks ->
            GreeksResult(
                assetClassGreeks = acGreeks,
                theta = theta ?: 0.0,
                rho = rho ?: 0.0,
            )
        },
        calculatedAt = completedAt ?: startedAt,
        computedOutputs = computedOutputsSnapshot.ifEmpty {
            setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL)
        },
        pvValue = pvValue,
        positionRisk = positionRiskSnapshot,
        jobId = jobId,
        valuationDate = valuationDate,
    )
}

private fun JobPhase.toResponse(): JobPhaseResponse = JobPhaseResponse(
    name = name.name,
    status = status.name,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    details = details.mapValues { (_, v) -> v?.toString() ?: "" },
    error = error,
)
