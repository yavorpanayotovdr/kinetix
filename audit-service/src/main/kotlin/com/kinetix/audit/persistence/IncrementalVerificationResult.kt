package com.kinetix.audit.persistence

data class IncrementalVerificationResult(
    val valid: Boolean,
    val lastHash: String?,
    val eventsVerified: Int,
)
