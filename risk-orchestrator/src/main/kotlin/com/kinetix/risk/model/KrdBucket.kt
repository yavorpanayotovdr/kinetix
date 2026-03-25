package com.kinetix.risk.model

import java.math.BigDecimal

data class KrdBucket(
    val tenorLabel: String,
    val tenorDays: Int,
    val dv01: BigDecimal,
)
