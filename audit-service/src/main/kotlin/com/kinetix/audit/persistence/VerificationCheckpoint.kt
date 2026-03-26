package com.kinetix.audit.persistence

import java.time.Instant

data class VerificationCheckpoint(
    val id: Long,
    val lastEventId: Long,
    val lastHash: String,
    val eventCount: Long,
    val verifiedAt: Instant,
)
