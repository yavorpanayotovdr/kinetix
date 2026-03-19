package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.stress.StressTestResult
import com.kinetix.regulatory.stress.StressTestResultRepository
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedStressTestResultRepository(private val db: Database? = null) : StressTestResultRepository {

    override suspend fun save(result: StressTestResult): Unit = newSuspendedTransaction(db = db) {
        StressTestResultsTable.insert {
            it[id] = result.id
            it[scenarioId] = result.scenarioId
            it[bookId] = result.bookId
            it[calculatedAt] = OffsetDateTime.ofInstant(result.calculatedAt, ZoneOffset.UTC)
            it[basePv] = result.basePv
            it[stressedPv] = result.stressedPv
            it[pnlImpact] = result.pnlImpact
            it[varImpact] = result.varImpact
            it[positionImpacts] = result.positionImpacts?.let { json -> Json.parseToJsonElement(json) }
            it[modelVersion] = result.modelVersion
        }
    }

    override suspend fun findByScenarioId(scenarioId: String): List<StressTestResult> =
        newSuspendedTransaction(db = db) {
            StressTestResultsTable
                .selectAll()
                .where { StressTestResultsTable.scenarioId eq scenarioId }
                .orderBy(StressTestResultsTable.calculatedAt, SortOrder.DESC)
                .map { it.toResult() }
        }

    override suspend fun findByBookId(bookId: String): List<StressTestResult> =
        newSuspendedTransaction(db = db) {
            StressTestResultsTable
                .selectAll()
                .where { StressTestResultsTable.bookId eq bookId }
                .orderBy(StressTestResultsTable.calculatedAt, SortOrder.DESC)
                .map { it.toResult() }
        }

    override suspend fun findById(id: String): StressTestResult? = newSuspendedTransaction(db = db) {
        StressTestResultsTable
            .selectAll()
            .where { StressTestResultsTable.id eq id }
            .firstOrNull()
            ?.toResult()
    }

    private fun ResultRow.toResult() = StressTestResult(
        id = this[StressTestResultsTable.id],
        scenarioId = this[StressTestResultsTable.scenarioId],
        bookId = this[StressTestResultsTable.bookId],
        calculatedAt = this[StressTestResultsTable.calculatedAt].toInstant(),
        basePv = this[StressTestResultsTable.basePv],
        stressedPv = this[StressTestResultsTable.stressedPv],
        pnlImpact = this[StressTestResultsTable.pnlImpact],
        varImpact = this[StressTestResultsTable.varImpact],
        positionImpacts = this[StressTestResultsTable.positionImpacts]?.let { Json.encodeToString(it) },
        modelVersion = this[StressTestResultsTable.modelVersion],
    )
}
