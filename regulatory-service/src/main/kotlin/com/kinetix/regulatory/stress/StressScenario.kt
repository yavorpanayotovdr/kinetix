package com.kinetix.regulatory.stress

import java.math.BigDecimal
import java.time.Instant

data class StressScenario(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: ScenarioStatus,
    val createdBy: String,
    val approvedBy: String?,
    val approvedAt: Instant?,
    val createdAt: Instant,
    val scenarioType: ScenarioType = ScenarioType.PARAMETRIC,
    val version: Int = 1,
    val parentScenarioId: String? = null,
    val correlationOverride: String? = null,
    val liquidityStressFactors: String? = null,
    val historicalPeriodId: String? = null,
    val targetLoss: BigDecimal? = null,
)
