package com.kinetix.correlation.persistence

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedCorrelationMatrixRepository(private val db: Database? = null) : CorrelationMatrixRepository {

    override suspend fun save(matrix: CorrelationMatrix): Unit = newSuspendedTransaction(db = db) {
        CorrelationMatrixTable.insert {
            it[labelsJson] = Json.encodeToString(JsonArray.serializer(), JsonArray(matrix.labels.map { l -> JsonPrimitive(l) }))
            it[valuesJson] = Json.encodeToString(JsonArray.serializer(), JsonArray(matrix.values.map { v -> JsonPrimitive(v) }))
            it[windowDays] = matrix.windowDays
            it[asOfDate] = matrix.asOfDate.atOffset(ZoneOffset.UTC)
            it[method] = matrix.method.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(labels: List<String>, windowDays: Int): CorrelationMatrix? =
        newSuspendedTransaction(db = db) {
            val sortedLabels = labels.sorted()
            val labelsStr = Json.encodeToString(JsonArray.serializer(), JsonArray(sortedLabels.map { JsonPrimitive(it) }))
            CorrelationMatrixTable
                .selectAll()
                .where {
                    (CorrelationMatrixTable.labelsJson eq labelsStr) and
                        (CorrelationMatrixTable.windowDays eq windowDays)
                }
                .orderBy(CorrelationMatrixTable.asOfDate, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toCorrelationMatrix()
        }

    override suspend fun findByTimeRange(
        labels: List<String>,
        windowDays: Int,
        from: Instant,
        to: Instant,
    ): List<CorrelationMatrix> = newSuspendedTransaction(db = db) {
        val sortedLabels = labels.sorted()
        val labelsStr = Json.encodeToString(JsonArray.serializer(), JsonArray(sortedLabels.map { JsonPrimitive(it) }))
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        CorrelationMatrixTable
            .selectAll()
            .where {
                (CorrelationMatrixTable.labelsJson eq labelsStr) and
                    (CorrelationMatrixTable.windowDays eq windowDays) and
                    (CorrelationMatrixTable.asOfDate greaterEq fromOffset) and
                    (CorrelationMatrixTable.asOfDate lessEq toOffset)
            }
            .orderBy(CorrelationMatrixTable.asOfDate, SortOrder.ASC)
            .map { it.toCorrelationMatrix() }
    }

    private fun ResultRow.toCorrelationMatrix(): CorrelationMatrix {
        val labels = Json.parseToJsonElement(this[CorrelationMatrixTable.labelsJson])
            .jsonArray.map { it.jsonPrimitive.content }
        val values = Json.parseToJsonElement(this[CorrelationMatrixTable.valuesJson])
            .jsonArray.map { it.jsonPrimitive.double }
        return CorrelationMatrix(
            labels = labels,
            values = values,
            windowDays = this[CorrelationMatrixTable.windowDays],
            asOfDate = this[CorrelationMatrixTable.asOfDate].toInstant(),
            method = EstimationMethod.valueOf(this[CorrelationMatrixTable.method]),
        )
    }
}
