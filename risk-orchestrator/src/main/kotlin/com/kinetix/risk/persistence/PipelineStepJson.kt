package com.kinetix.risk.persistence

import kotlinx.serialization.Serializable

@Serializable
data class PipelineStepJson(
    val name: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
    val error: String? = null,
)
