package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ValuationJobDetailResponse(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val pvValue: Double? = null,
    val phases: List<JobPhaseResponse> = emptyList(),
    val error: String? = null,
    val valuationDate: String? = null,
    val runLabel: String? = null,
    val promotedAt: String? = null,
    val promotedBy: String? = null,
    val currentPhase: String? = null,
)
