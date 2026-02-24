package com.kinetix.risk.model

import java.time.Instant

data class JobStep(
    val name: JobStepName,
    val status: RunStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val durationMs: Long? = null,
    val details: Map<String, Any?> = emptyMap(),
    val error: String? = null,
)
