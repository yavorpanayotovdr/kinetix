package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object RiskBudgetAllocationsTable : Table("risk_budget_allocations") {
    val id = varchar("id", 36)
    val entityLevel = varchar("entity_level", 16)
    val entityId = varchar("entity_id", 64)
    val budgetType = varchar("budget_type", 16)
    val budgetPeriod = varchar("budget_period", 16)
    val budgetAmount = decimal("budget_amount", 24, 6)
    val effectiveFrom = date("effective_from")
    val effectiveTo = date("effective_to").nullable()
    val allocatedBy = varchar("allocated_by", 255)
    val allocationNote = text("allocation_note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
