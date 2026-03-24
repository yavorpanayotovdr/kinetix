package com.kinetix.position.fix

import com.kinetix.common.model.Side
import java.math.BigDecimal
import java.time.Instant

data class ExecutionCostAnalysis(
    val orderId: String,
    val bookId: String,
    val instrumentId: String,
    val completedAt: Instant,
    val arrivalPrice: BigDecimal,
    val averageFillPrice: BigDecimal,
    val side: Side,
    val totalQty: BigDecimal,
    val metrics: ExecutionCostMetrics,
)
