package com.kinetix.risk.persistence

import com.kinetix.risk.model.*
import com.kinetix.risk.service.CalculationJobRecorder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedCalculationJobRecorder(private val db: Database? = null) : CalculationJobRecorder {

    override suspend fun save(job: CalculationJob): Unit = newSuspendedTransaction(db = db) {
        CalculationJobsTable.insert {
            it[jobId] = job.jobId
            it[portfolioId] = job.portfolioId
            it[triggerType] = job.triggerType.name
            it[status] = job.status.name
            it[startedAt] = OffsetDateTime.ofInstant(job.startedAt, ZoneOffset.UTC)
            it[completedAt] = job.completedAt?.let { ts -> OffsetDateTime.ofInstant(ts, ZoneOffset.UTC) }
            it[durationMs] = job.durationMs
            it[calculationType] = job.calculationType
            it[confidenceLevel] = job.confidenceLevel
            it[varValue] = job.varValue
            it[expectedShortfall] = job.expectedShortfall
            it[steps] = job.steps.map { step -> step.toJson() }
            it[error] = job.error
        }
    }

    override suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int,
        offset: Int,
    ): List<CalculationJob> = newSuspendedTransaction(db = db) {
        CalculationJobsTable
            .selectAll()
            .where { CalculationJobsTable.portfolioId eq portfolioId }
            .orderBy(CalculationJobsTable.startedAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toCalculationJob() }
    }

    override suspend fun findByJobId(jobId: UUID): CalculationJob? = newSuspendedTransaction(db = db) {
        CalculationJobsTable
            .selectAll()
            .where { CalculationJobsTable.jobId eq jobId }
            .firstOrNull()
            ?.toCalculationJob()
    }

    private fun JobStep.toJson(): JobStepJson = JobStepJson(
        name = name.name,
        status = status.name,
        startedAt = startedAt.toString(),
        completedAt = completedAt?.toString(),
        durationMs = durationMs,
        details = details.mapValues { (_, v) -> v?.toString() ?: "" },
        error = error,
    )

    private fun JobStepJson.toDomain(): JobStep = JobStep(
        name = JobStepName.valueOf(name),
        status = RunStatus.valueOf(status),
        startedAt = Instant.parse(startedAt),
        completedAt = completedAt?.let { Instant.parse(it) },
        durationMs = durationMs,
        details = details,
        error = error,
    )

    private fun ResultRow.toCalculationJob(): CalculationJob = CalculationJob(
        jobId = this[CalculationJobsTable.jobId],
        portfolioId = this[CalculationJobsTable.portfolioId],
        triggerType = TriggerType.valueOf(this[CalculationJobsTable.triggerType]),
        status = RunStatus.valueOf(this[CalculationJobsTable.status]),
        startedAt = this[CalculationJobsTable.startedAt].toInstant(),
        completedAt = this[CalculationJobsTable.completedAt]?.toInstant(),
        durationMs = this[CalculationJobsTable.durationMs],
        calculationType = this[CalculationJobsTable.calculationType],
        confidenceLevel = this[CalculationJobsTable.confidenceLevel],
        varValue = this[CalculationJobsTable.varValue],
        expectedShortfall = this[CalculationJobsTable.expectedShortfall],
        steps = this[CalculationJobsTable.steps].map { it.toDomain() },
        error = this[CalculationJobsTable.error],
    )
}
