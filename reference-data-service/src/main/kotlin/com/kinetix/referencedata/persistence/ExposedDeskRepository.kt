package com.kinetix.referencedata.persistence

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
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

class ExposedDeskRepository(
    private val db: Database? = null,
) : DeskRepository {

    override suspend fun save(desk: Desk): Unit = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val existing = DesksTable
            .selectAll()
            .where { DesksTable.id eq desk.id.value }
            .singleOrNull()

        if (existing != null) {
            DesksTable.update({ DesksTable.id eq desk.id.value }) {
                it[name] = desk.name
                it[divisionId] = desk.divisionId.value
                it[deskHead] = desk.deskHead
                it[description] = desk.description
                it[updatedAt] = now
            }
        } else {
            DesksTable.insert {
                it[id] = desk.id.value
                it[name] = desk.name
                it[divisionId] = desk.divisionId.value
                it[deskHead] = desk.deskHead
                it[description] = desk.description
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    override suspend fun findById(id: DeskId): Desk? =
        newSuspendedTransaction(db = db) {
            DesksTable
                .selectAll()
                .where { DesksTable.id eq id.value }
                .singleOrNull()
                ?.toDesk()
        }

    override suspend fun findAll(): List<Desk> =
        newSuspendedTransaction(db = db) {
            DesksTable
                .selectAll()
                .orderBy(DesksTable.name, SortOrder.ASC)
                .map { it.toDesk() }
        }

    override suspend fun findByDivisionId(divisionId: DivisionId): List<Desk> =
        newSuspendedTransaction(db = db) {
            DesksTable
                .selectAll()
                .where { DesksTable.divisionId eq divisionId.value }
                .orderBy(DesksTable.name, SortOrder.ASC)
                .map { it.toDesk() }
        }

    private fun ResultRow.toDesk() = Desk(
        id = DeskId(this[DesksTable.id]),
        name = this[DesksTable.name],
        divisionId = DivisionId(this[DesksTable.divisionId]),
        deskHead = this[DesksTable.deskHead],
        description = this[DesksTable.description],
    )
}
