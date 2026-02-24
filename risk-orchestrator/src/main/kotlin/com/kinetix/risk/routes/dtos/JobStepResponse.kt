package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class JobStepResponse(
    val name: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
    val error: String? = null,
)
