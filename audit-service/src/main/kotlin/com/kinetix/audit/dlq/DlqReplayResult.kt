package com.kinetix.audit.dlq

import kotlinx.serialization.Serializable

@Serializable
data class DlqReplayResult(
    val successCount: Int,
    val failureCount: Int,
    val total: Int,
)
