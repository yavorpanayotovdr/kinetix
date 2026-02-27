package com.kinetix.risk.model

import java.time.Instant
import java.util.UUID

data class ValuationJob(
    val jobId: UUID,
    val portfolioId: String,
    val triggerType: TriggerType,
    val status: RunStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val durationMs: Long? = null,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val pvValue: Double? = null,
    val steps: List<JobStep> = emptyList(),
    val error: String? = null,
)
