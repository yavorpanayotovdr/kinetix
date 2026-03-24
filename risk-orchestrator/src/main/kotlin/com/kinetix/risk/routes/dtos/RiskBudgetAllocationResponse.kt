package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RiskBudgetAllocationResponse(
    val id: String,
    val entityLevel: String,
    val entityId: String,
    val budgetType: String,
    val budgetPeriod: String,
    val budgetAmount: String,
    val effectiveFrom: String,
    val effectiveTo: String?,
    val allocatedBy: String,
    val allocationNote: String?,
)
