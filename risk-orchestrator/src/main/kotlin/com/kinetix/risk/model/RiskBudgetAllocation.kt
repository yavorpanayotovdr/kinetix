package com.kinetix.risk.model

import java.math.BigDecimal
import java.time.LocalDate

data class RiskBudgetAllocation(
    val id: String,
    val entityLevel: HierarchyLevel,
    val entityId: String,
    val budgetType: String,
    val budgetPeriod: BudgetPeriod,
    val budgetAmount: BigDecimal,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
    val allocatedBy: String,
    val allocationNote: String?,
)
