package com.kinetix.position.fix

import java.math.BigDecimal

data class ExecutionCostMetrics(
    val slippageBps: BigDecimal,
    val marketImpactBps: BigDecimal?,
    val timingCostBps: BigDecimal?,
    val totalCostBps: BigDecimal,
)
