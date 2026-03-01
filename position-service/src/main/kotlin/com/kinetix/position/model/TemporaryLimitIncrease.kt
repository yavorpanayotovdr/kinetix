package com.kinetix.position.model

import java.math.BigDecimal
import java.time.Instant

data class TemporaryLimitIncrease(
    val id: String,
    val limitId: String,
    val newValue: BigDecimal,
    val approvedBy: String,
    val expiresAt: Instant,
    val reason: String,
    val createdAt: Instant,
)
