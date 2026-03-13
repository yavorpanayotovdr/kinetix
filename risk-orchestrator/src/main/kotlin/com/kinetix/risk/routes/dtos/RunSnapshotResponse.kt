package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RunSnapshotResponse(
    val jobId: String?,
    val label: String,
    val valuationDate: String,
    val calcType: String?,
    val confLevel: String?,
    val varValue: String?,
    val es: String?,
    val pv: String?,
    val delta: String?,
    val gamma: String?,
    val vega: String?,
    val theta: String?,
    val rho: String?,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val positionRisk: List<PositionRiskDto>,
    val modelVersion: String?,
    val parameters: Map<String, String>,
    val calculatedAt: String?,
)
