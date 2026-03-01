package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.stress.ScenarioStatus
import com.kinetix.regulatory.stress.StressScenario
import com.kinetix.regulatory.stress.StressScenarioRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedStressScenarioRepository(private val db: Database? = null) : StressScenarioRepository {

    override suspend fun save(scenario: StressScenario): Unit = newSuspendedTransaction(db = db) {
        val existing = StressScenariosTable
            .selectAll()
            .where { StressScenariosTable.id eq scenario.id }
            .firstOrNull()

        if (existing != null) {
            StressScenariosTable.update({ StressScenariosTable.id eq scenario.id }) {
                it[status] = scenario.status.name
                it[approvedBy] = scenario.approvedBy
                it[approvedAt] = scenario.approvedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
            }
        } else {
            StressScenariosTable.insert {
                it[id] = scenario.id
                it[name] = scenario.name
                it[description] = scenario.description
                it[shocks] = scenario.shocks
                it[status] = scenario.status.name
                it[createdBy] = scenario.createdBy
                it[approvedBy] = scenario.approvedBy
                it[approvedAt] = scenario.approvedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
                it[createdAt] = OffsetDateTime.ofInstant(scenario.createdAt, ZoneOffset.UTC)
            }
        }
    }

    override suspend fun findById(id: String): StressScenario? = newSuspendedTransaction(db = db) {
        StressScenariosTable
            .selectAll()
            .where { StressScenariosTable.id eq id }
            .map { it.toScenario() }
            .firstOrNull()
    }

    override suspend fun findAll(): List<StressScenario> = newSuspendedTransaction(db = db) {
        StressScenariosTable
            .selectAll()
            .orderBy(StressScenariosTable.createdAt, SortOrder.DESC)
            .map { it.toScenario() }
    }

    override suspend fun findByStatus(status: ScenarioStatus): List<StressScenario> =
        newSuspendedTransaction(db = db) {
            StressScenariosTable
                .selectAll()
                .where { StressScenariosTable.status eq status.name }
                .orderBy(StressScenariosTable.createdAt, SortOrder.DESC)
                .map { it.toScenario() }
        }

    private fun ResultRow.toScenario() = StressScenario(
        id = this[StressScenariosTable.id],
        name = this[StressScenariosTable.name],
        description = this[StressScenariosTable.description],
        shocks = this[StressScenariosTable.shocks],
        status = ScenarioStatus.valueOf(this[StressScenariosTable.status]),
        createdBy = this[StressScenariosTable.createdBy],
        approvedBy = this[StressScenariosTable.approvedBy],
        approvedAt = this[StressScenariosTable.approvedAt]?.toInstant(),
        createdAt = this[StressScenariosTable.createdAt].toInstant(),
    )
}
