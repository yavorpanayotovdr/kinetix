package com.kinetix.audit.persistence

import kotlinx.serialization.Serializable

@Serializable
data class ChainVerificationResult(
    val valid: Boolean,
    val eventCount: Int,
)
