package com.kinetix.referencedata.persistence

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedDivisionRepository(
    private val db: Database? = null,
) : DivisionRepository {

    override suspend fun save(division: Division): Unit = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val existing = DivisionsTable
            .selectAll()
            .where { DivisionsTable.id eq division.id.value }
            .singleOrNull()

        if (existing != null) {
            DivisionsTable.update({ DivisionsTable.id eq division.id.value }) {
                it[name] = division.name
                it[description] = division.description
                it[updatedAt] = now
            }
        } else {
            DivisionsTable.insert {
                it[id] = division.id.value
                it[name] = division.name
                it[description] = division.description
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    override suspend fun findById(id: DivisionId): Division? =
        newSuspendedTransaction(db = db) {
            DivisionsTable
                .selectAll()
                .where { DivisionsTable.id eq id.value }
                .singleOrNull()
                ?.toDivision()
        }

    override suspend fun findByName(name: String): Division? =
        newSuspendedTransaction(db = db) {
            DivisionsTable
                .selectAll()
                .where { DivisionsTable.name eq name }
                .singleOrNull()
                ?.toDivision()
        }

    override suspend fun findAll(): List<Division> =
        newSuspendedTransaction(db = db) {
            DivisionsTable
                .selectAll()
                .orderBy(DivisionsTable.name, SortOrder.ASC)
                .map { it.toDivision() }
        }

    private fun ResultRow.toDivision() = Division(
        id = DivisionId(this[DivisionsTable.id]),
        name = this[DivisionsTable.name],
        description = this[DivisionsTable.description],
    )
}
