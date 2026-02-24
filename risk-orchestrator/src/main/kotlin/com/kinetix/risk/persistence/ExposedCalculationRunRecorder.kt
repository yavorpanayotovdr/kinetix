package com.kinetix.risk.persistence

import com.kinetix.risk.model.*
import com.kinetix.risk.service.CalculationRunRecorder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedCalculationRunRecorder(private val db: Database? = null) : CalculationRunRecorder {

    override suspend fun save(run: CalculationRun): Unit = newSuspendedTransaction(db = db) {
        CalculationRunsTable.insert {
            it[runId] = run.runId
            it[portfolioId] = run.portfolioId
            it[triggerType] = run.triggerType.name
            it[status] = run.status.name
            it[startedAt] = OffsetDateTime.ofInstant(run.startedAt, ZoneOffset.UTC)
            it[completedAt] = run.completedAt?.let { ts -> OffsetDateTime.ofInstant(ts, ZoneOffset.UTC) }
            it[durationMs] = run.durationMs
            it[calculationType] = run.calculationType
            it[confidenceLevel] = run.confidenceLevel
            it[varValue] = run.varValue
            it[expectedShortfall] = run.expectedShortfall
            it[steps] = run.steps.map { step -> step.toJson() }
            it[error] = run.error
        }
    }

    override suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int,
        offset: Int,
    ): List<CalculationRun> = newSuspendedTransaction(db = db) {
        CalculationRunsTable
            .selectAll()
            .where { CalculationRunsTable.portfolioId eq portfolioId }
            .orderBy(CalculationRunsTable.startedAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toCalculationRun() }
    }

    override suspend fun findByRunId(runId: UUID): CalculationRun? = newSuspendedTransaction(db = db) {
        CalculationRunsTable
            .selectAll()
            .where { CalculationRunsTable.runId eq runId }
            .firstOrNull()
            ?.toCalculationRun()
    }

    private fun PipelineStep.toJson(): PipelineStepJson = PipelineStepJson(
        name = name.name,
        status = status.name,
        startedAt = startedAt.toString(),
        completedAt = completedAt?.toString(),
        durationMs = durationMs,
        details = details.mapValues { (_, v) -> v?.toString() ?: "" },
        error = error,
    )

    private fun PipelineStepJson.toDomain(): PipelineStep = PipelineStep(
        name = PipelineStepName.valueOf(name),
        status = RunStatus.valueOf(status),
        startedAt = Instant.parse(startedAt),
        completedAt = completedAt?.let { Instant.parse(it) },
        durationMs = durationMs,
        details = details,
        error = error,
    )

    private fun ResultRow.toCalculationRun(): CalculationRun = CalculationRun(
        runId = this[CalculationRunsTable.runId],
        portfolioId = this[CalculationRunsTable.portfolioId],
        triggerType = TriggerType.valueOf(this[CalculationRunsTable.triggerType]),
        status = RunStatus.valueOf(this[CalculationRunsTable.status]),
        startedAt = this[CalculationRunsTable.startedAt].toInstant(),
        completedAt = this[CalculationRunsTable.completedAt]?.toInstant(),
        durationMs = this[CalculationRunsTable.durationMs],
        calculationType = this[CalculationRunsTable.calculationType],
        confidenceLevel = this[CalculationRunsTable.confidenceLevel],
        varValue = this[CalculationRunsTable.varValue],
        expectedShortfall = this[CalculationRunsTable.expectedShortfall],
        steps = this[CalculationRunsTable.steps].map { it.toDomain() },
        error = this[CalculationRunsTable.error],
    )
}
