package com.kinetix.risk.mapper

import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.RunSnapshot
import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.model.ValuationResult
import java.time.LocalDate

fun ValuationJob.toRunSnapshot(label: String): RunSnapshot = RunSnapshot(
    jobId = jobId,
    label = label,
    valuationDate = valuationDate,
    calculationType = calculationType?.let { runCatching { CalculationType.valueOf(it) }.getOrNull() },
    confidenceLevel = confidenceLevel?.let { runCatching { ConfidenceLevel.valueOf(it) }.getOrNull() },
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    positionRisks = positionRiskSnapshot,
    componentBreakdowns = componentBreakdownSnapshot,
    modelVersion = null,
    parameters = buildMap {
        calculationType?.let { put("calculationType", it) }
        confidenceLevel?.let { put("confidenceLevel", it) }
    },
    calculatedAt = completedAt ?: startedAt,
)

fun ValuationResult.toRunSnapshot(label: String, valuationDate: LocalDate): RunSnapshot = RunSnapshot(
    jobId = jobId,
    label = label,
    valuationDate = this.valuationDate ?: valuationDate,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    delta = greeks?.assetClassGreeks?.sumOf { it.delta },
    gamma = greeks?.assetClassGreeks?.sumOf { it.gamma },
    vega = greeks?.assetClassGreeks?.sumOf { it.vega },
    theta = greeks?.theta,
    rho = greeks?.rho,
    positionRisks = positionRisk,
    componentBreakdowns = componentBreakdown,
    modelVersion = modelVersion,
    parameters = buildMap {
        put("calculationType", calculationType.name)
        put("confidenceLevel", confidenceLevel.name)
    },
    calculatedAt = calculatedAt,
)
