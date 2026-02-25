package com.kinetix.risk.persistence

import com.kinetix.risk.model.*
import com.kinetix.risk.service.ValuationJobRecorder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedValuationJobRecorder(private val db: Database? = null) : ValuationJobRecorder {

    override suspend fun save(job: ValuationJob): Unit = newSuspendedTransaction(db = db) {
        ValuationJobsTable.insert {
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

    override suspend fun update(job: ValuationJob): Unit = newSuspendedTransaction(db = db) {
        ValuationJobsTable.update({ ValuationJobsTable.jobId eq job.jobId }) {
            it[status] = job.status.name
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
        from: Instant?,
        to: Instant?,
    ): List<ValuationJob> = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where {
                var condition = ValuationJobsTable.portfolioId eq portfolioId
                if (from != null) {
                    condition = condition and (ValuationJobsTable.startedAt greaterEq OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                }
                if (to != null) {
                    condition = condition and (ValuationJobsTable.startedAt lessEq OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                condition
            }
            .orderBy(ValuationJobsTable.startedAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toValuationJob() }
    }

    override suspend fun countByPortfolioId(
        portfolioId: String,
        from: Instant?,
        to: Instant?,
    ): Long = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where {
                var condition = ValuationJobsTable.portfolioId eq portfolioId
                if (from != null) {
                    condition = condition and (ValuationJobsTable.startedAt greaterEq OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                }
                if (to != null) {
                    condition = condition and (ValuationJobsTable.startedAt lessEq OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                condition
            }
            .count()
    }

    override suspend fun findByJobId(jobId: UUID): ValuationJob? = newSuspendedTransaction(db = db) {
        ValuationJobsTable
            .selectAll()
            .where { ValuationJobsTable.jobId eq jobId }
            .firstOrNull()
            ?.toValuationJob()
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

    private fun ResultRow.toValuationJob(): ValuationJob = ValuationJob(
        jobId = this[ValuationJobsTable.jobId],
        portfolioId = this[ValuationJobsTable.portfolioId],
        triggerType = TriggerType.valueOf(this[ValuationJobsTable.triggerType]),
        status = RunStatus.valueOf(this[ValuationJobsTable.status]),
        startedAt = this[ValuationJobsTable.startedAt].toInstant(),
        completedAt = this[ValuationJobsTable.completedAt]?.toInstant(),
        durationMs = this[ValuationJobsTable.durationMs],
        calculationType = this[ValuationJobsTable.calculationType],
        confidenceLevel = this[ValuationJobsTable.confidenceLevel],
        varValue = this[ValuationJobsTable.varValue],
        expectedShortfall = this[ValuationJobsTable.expectedShortfall],
        steps = this[ValuationJobsTable.steps].map { it.toDomain() },
        error = this[ValuationJobsTable.error],
    )
}
