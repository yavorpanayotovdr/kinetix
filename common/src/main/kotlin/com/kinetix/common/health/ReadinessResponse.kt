package com.kinetix.common.health

import kotlinx.serialization.Serializable

@Serializable
data class ReadinessResponse(
    val status: String,
    val checks: Map<String, CheckResult>,
    val consumers: Map<String, ConsumerHealth> = emptyMap(),
)

@Serializable
data class CheckResult(
    val status: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ConsumerHealth(
    val lastProcessedAt: String?,
    val recordsProcessedTotal: Long,
    val recordsSentToDlqTotal: Long,
    val lastErrorAt: String?,
    val consecutiveErrorCount: Long,
)
