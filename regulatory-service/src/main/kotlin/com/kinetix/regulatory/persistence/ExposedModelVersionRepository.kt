package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.governance.ModelVersion
import com.kinetix.regulatory.governance.ModelVersionRepository
import com.kinetix.regulatory.governance.ModelVersionStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedModelVersionRepository(private val db: Database? = null) : ModelVersionRepository {

    override suspend fun save(modelVersion: ModelVersion): Unit = newSuspendedTransaction(db = db) {
        val existing = ModelVersionsTable
            .selectAll()
            .where { ModelVersionsTable.id eq modelVersion.id }
            .firstOrNull()

        if (existing != null) {
            ModelVersionsTable.update({ ModelVersionsTable.id eq modelVersion.id }) {
                it[status] = modelVersion.status.name
                it[approvedBy] = modelVersion.approvedBy
                it[approvedAt] = modelVersion.approvedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
            }
        } else {
            ModelVersionsTable.insert {
                it[id] = modelVersion.id
                it[modelName] = modelVersion.modelName
                it[version] = modelVersion.version
                it[status] = modelVersion.status.name
                it[parameters] = modelVersion.parameters
                it[approvedBy] = modelVersion.approvedBy
                it[approvedAt] = modelVersion.approvedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
                it[createdAt] = OffsetDateTime.ofInstant(modelVersion.createdAt, ZoneOffset.UTC)
            }
        }
    }

    override suspend fun findById(id: String): ModelVersion? = newSuspendedTransaction(db = db) {
        ModelVersionsTable
            .selectAll()
            .where { ModelVersionsTable.id eq id }
            .map { it.toModelVersion() }
            .firstOrNull()
    }

    override suspend fun findAll(): List<ModelVersion> = newSuspendedTransaction(db = db) {
        ModelVersionsTable
            .selectAll()
            .orderBy(ModelVersionsTable.createdAt, SortOrder.DESC)
            .map { it.toModelVersion() }
    }

    private fun ResultRow.toModelVersion() = ModelVersion(
        id = this[ModelVersionsTable.id],
        modelName = this[ModelVersionsTable.modelName],
        version = this[ModelVersionsTable.version],
        status = ModelVersionStatus.valueOf(this[ModelVersionsTable.status]),
        parameters = this[ModelVersionsTable.parameters],
        approvedBy = this[ModelVersionsTable.approvedBy],
        approvedAt = this[ModelVersionsTable.approvedAt]?.toInstant(),
        createdAt = this[ModelVersionsTable.createdAt].toInstant(),
    )
}
