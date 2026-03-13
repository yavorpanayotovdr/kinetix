package com.kinetix.risk.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RunSnapshot(
    val jobId: UUID?,
    val label: String,
    val valuationDate: LocalDate,
    val calculationType: CalculationType?,
    val confidenceLevel: ConfidenceLevel?,
    val varValue: Double?,
    val expectedShortfall: Double?,
    val pvValue: Double?,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
    val theta: Double?,
    val rho: Double?,
    val positionRisks: List<PositionRisk>,
    val componentBreakdowns: List<ComponentBreakdown>,
    val modelVersion: String?,
    val parameters: Map<String, String>,
    val calculatedAt: Instant?,
)
