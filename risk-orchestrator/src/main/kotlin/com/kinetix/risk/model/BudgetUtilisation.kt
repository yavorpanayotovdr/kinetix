package com.kinetix.risk.model

import java.math.BigDecimal
import java.time.Instant

data class BudgetUtilisation(
    val entityLevel: HierarchyLevel,
    val entityId: String,
    val budgetType: String,
    val budgetAmount: BigDecimal,
    val currentVar: BigDecimal,
    val utilisationPct: BigDecimal,
    val breachStatus: BreachStatus,
    val updatedAt: Instant,
)
