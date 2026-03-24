package com.kinetix.position.fix

import java.time.Instant

data class PrimeBrokerReconciliation(
    val reconciliationDate: String,
    val bookId: String,
    val status: String,
    val totalPositions: Int,
    val matchedCount: Int,
    val breakCount: Int,
    val breaks: List<ReconciliationBreak>,
    val reconciledAt: Instant,
)
