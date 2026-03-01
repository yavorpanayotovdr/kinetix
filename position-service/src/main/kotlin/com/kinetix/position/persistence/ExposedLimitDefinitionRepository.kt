package com.kinetix.position.persistence

import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedLimitDefinitionRepository(private val db: Database? = null) : LimitDefinitionRepository {

    override suspend fun findByEntityAndType(
        entityId: String,
        level: LimitLevel,
        limitType: LimitType,
    ): LimitDefinition? = newSuspendedTransaction(db = db) {
        LimitDefinitionsTable
            .selectAll()
            .where {
                (LimitDefinitionsTable.entityId eq entityId) and
                    (LimitDefinitionsTable.level eq level.name) and
                    (LimitDefinitionsTable.limitType eq limitType.name) and
                    (LimitDefinitionsTable.active eq true)
            }
            .singleOrNull()
            ?.toModel()
    }

    override suspend fun findAll(): List<LimitDefinition> = newSuspendedTransaction(db = db) {
        LimitDefinitionsTable
            .selectAll()
            .map { it.toModel() }
    }

    override suspend fun save(limitDefinition: LimitDefinition): Unit = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        LimitDefinitionsTable.upsert(LimitDefinitionsTable.id) {
            it[id] = limitDefinition.id
            it[level] = limitDefinition.level.name
            it[entityId] = limitDefinition.entityId
            it[limitType] = limitDefinition.limitType.name
            it[limitValue] = limitDefinition.limitValue
            it[intradayLimit] = limitDefinition.intradayLimit
            it[overnightLimit] = limitDefinition.overnightLimit
            it[active] = limitDefinition.active
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    override suspend fun update(limitDefinition: LimitDefinition): Unit = newSuspendedTransaction(db = db) {
        LimitDefinitionsTable.update({ LimitDefinitionsTable.id eq limitDefinition.id }) {
            it[level] = limitDefinition.level.name
            it[entityId] = limitDefinition.entityId
            it[limitType] = limitDefinition.limitType.name
            it[limitValue] = limitDefinition.limitValue
            it[intradayLimit] = limitDefinition.intradayLimit
            it[overnightLimit] = limitDefinition.overnightLimit
            it[active] = limitDefinition.active
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findById(id: String): LimitDefinition? = newSuspendedTransaction(db = db) {
        LimitDefinitionsTable
            .selectAll()
            .where { LimitDefinitionsTable.id eq id }
            .singleOrNull()
            ?.toModel()
    }

    private fun ResultRow.toModel(): LimitDefinition = LimitDefinition(
        id = this[LimitDefinitionsTable.id],
        level = LimitLevel.valueOf(this[LimitDefinitionsTable.level]),
        entityId = this[LimitDefinitionsTable.entityId],
        limitType = LimitType.valueOf(this[LimitDefinitionsTable.limitType]),
        limitValue = this[LimitDefinitionsTable.limitValue],
        intradayLimit = this[LimitDefinitionsTable.intradayLimit],
        overnightLimit = this[LimitDefinitionsTable.overnightLimit],
        active = this[LimitDefinitionsTable.active],
    )
}
