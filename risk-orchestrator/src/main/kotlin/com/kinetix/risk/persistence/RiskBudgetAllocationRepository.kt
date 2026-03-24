package com.kinetix.risk.persistence

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.RiskBudgetAllocation
import java.time.LocalDate

interface RiskBudgetAllocationRepository {
    suspend fun save(allocation: RiskBudgetAllocation)
    suspend fun update(allocation: RiskBudgetAllocation)
    suspend fun findById(id: String): RiskBudgetAllocation?
    suspend fun findEffective(
        level: HierarchyLevel,
        entityId: String,
        budgetType: String,
        asOf: LocalDate,
    ): RiskBudgetAllocation?
    suspend fun findAll(level: HierarchyLevel? = null, entityId: String? = null): List<RiskBudgetAllocation>
    suspend fun delete(id: String)
}
