package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CalculationJobSummaryResponse(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val calculationType: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
)
