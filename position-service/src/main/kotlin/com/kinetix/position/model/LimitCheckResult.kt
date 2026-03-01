package com.kinetix.position.model

import java.math.BigDecimal

data class LimitCheckResult(
    val status: LimitCheckStatus,
    val limitValue: BigDecimal? = null,
    val effectiveLimit: BigDecimal? = null,
    val currentExposure: BigDecimal? = null,
    val breachedAt: LimitLevel? = null,
    val message: String? = null,
)
