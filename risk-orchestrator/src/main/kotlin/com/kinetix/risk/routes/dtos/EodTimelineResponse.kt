package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class EodTimelineEntryDto(
    val valuationDate: String,
    val jobId: String,
    val varValue: Double?,
    val expectedShortfall: Double?,
    val pvValue: Double?,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
    val theta: Double?,
    val rho: Double?,
    val promotedAt: String?,
    val promotedBy: String?,
    val varChange: Double?,
    val varChangePct: Double?,
    val esChange: Double?,
    val calculationType: String?,
    val confidenceLevel: Double?,
)

@Serializable
data class EodTimelineResponse(
    val portfolioId: String,
    val from: String,
    val to: String,
    val entries: List<EodTimelineEntryDto>,
)
