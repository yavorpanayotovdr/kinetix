package com.kinetix.risk.persistence

import com.kinetix.risk.model.BudgetPeriod
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.RiskBudgetAllocation
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedRiskBudgetAllocationRepository(
    private val db: Database? = null,
) : RiskBudgetAllocationRepository {

    override suspend fun save(allocation: RiskBudgetAllocation): Unit = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RiskBudgetAllocationsTable.insert {
            it[id] = allocation.id
            it[entityLevel] = allocation.entityLevel.name
            it[entityId] = allocation.entityId
            it[budgetType] = allocation.budgetType
            it[budgetPeriod] = allocation.budgetPeriod.name
            it[budgetAmount] = allocation.budgetAmount
            it[effectiveFrom] = allocation.effectiveFrom.toKotlinLocalDate()
            it[effectiveTo] = allocation.effectiveTo?.toKotlinLocalDate()
            it[allocatedBy] = allocation.allocatedBy
            it[allocationNote] = allocation.allocationNote
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    override suspend fun update(allocation: RiskBudgetAllocation): Unit = newSuspendedTransaction(db = db) {
        RiskBudgetAllocationsTable.update({ RiskBudgetAllocationsTable.id eq allocation.id }) {
            it[budgetAmount] = allocation.budgetAmount
            it[effectiveTo] = allocation.effectiveTo?.toKotlinLocalDate()
            it[allocationNote] = allocation.allocationNote
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findById(id: String): RiskBudgetAllocation? = newSuspendedTransaction(db = db) {
        RiskBudgetAllocationsTable
            .selectAll()
            .where { RiskBudgetAllocationsTable.id eq id }
            .singleOrNull()
            ?.toModel()
    }

    override suspend fun findEffective(
        level: HierarchyLevel,
        entityId: String,
        budgetType: String,
        asOf: LocalDate,
    ): RiskBudgetAllocation? = newSuspendedTransaction(db = db) {
        val kotlinAsOf = asOf.toKotlinLocalDate()
        RiskBudgetAllocationsTable
            .selectAll()
            .where {
                (RiskBudgetAllocationsTable.entityLevel eq level.name) and
                    (RiskBudgetAllocationsTable.entityId eq entityId) and
                    (RiskBudgetAllocationsTable.budgetType eq budgetType) and
                    (RiskBudgetAllocationsTable.effectiveFrom lessEq kotlinAsOf) and
                    (
                        RiskBudgetAllocationsTable.effectiveTo.isNull() or
                            (RiskBudgetAllocationsTable.effectiveTo greaterEq kotlinAsOf)
                        )
            }
            .orderBy(RiskBudgetAllocationsTable.effectiveFrom, org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toModel()
    }

    override suspend fun findAll(
        level: HierarchyLevel?,
        entityId: String?,
    ): List<RiskBudgetAllocation> = newSuspendedTransaction(db = db) {
        RiskBudgetAllocationsTable
            .selectAll()
            .let { query ->
                when {
                    level != null && entityId != null ->
                        query.where {
                            (RiskBudgetAllocationsTable.entityLevel eq level.name) and
                                (RiskBudgetAllocationsTable.entityId eq entityId)
                        }
                    level != null ->
                        query.where { RiskBudgetAllocationsTable.entityLevel eq level.name }
                    entityId != null ->
                        query.where { RiskBudgetAllocationsTable.entityId eq entityId }
                    else -> query
                }
            }
            .map { it.toModel() }
    }

    override suspend fun delete(id: String): Unit = newSuspendedTransaction(db = db) {
        RiskBudgetAllocationsTable.deleteWhere { RiskBudgetAllocationsTable.id eq id }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toModel() = RiskBudgetAllocation(
        id = this[RiskBudgetAllocationsTable.id],
        entityLevel = HierarchyLevel.valueOf(this[RiskBudgetAllocationsTable.entityLevel]),
        entityId = this[RiskBudgetAllocationsTable.entityId],
        budgetType = this[RiskBudgetAllocationsTable.budgetType],
        budgetPeriod = BudgetPeriod.valueOf(this[RiskBudgetAllocationsTable.budgetPeriod]),
        budgetAmount = this[RiskBudgetAllocationsTable.budgetAmount],
        effectiveFrom = this[RiskBudgetAllocationsTable.effectiveFrom].toJavaLocalDate(),
        effectiveTo = this[RiskBudgetAllocationsTable.effectiveTo]?.toJavaLocalDate(),
        allocatedBy = this[RiskBudgetAllocationsTable.allocatedBy],
        allocationNote = this[RiskBudgetAllocationsTable.allocationNote],
    )

    private fun kotlinx.datetime.LocalDate.toJavaLocalDate(): LocalDate =
        LocalDate.of(year, monthNumber, dayOfMonth)
}
