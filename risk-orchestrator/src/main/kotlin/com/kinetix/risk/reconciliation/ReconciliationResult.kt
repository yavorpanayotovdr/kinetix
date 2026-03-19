package com.kinetix.risk.reconciliation

import java.time.Instant

data class ReconciliationResult(
    val since: Instant,
    val tradeCount: Long,
    val auditCount: Long,
    val matched: Boolean,
)
