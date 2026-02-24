package com.kinetix.risk.model

import java.time.Instant
import java.util.UUID

data class CalculationRun(
    val runId: UUID,
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
    val steps: List<PipelineStep> = emptyList(),
    val error: String? = null,
)
