package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.submission.RegulatorySubmission
import com.kinetix.regulatory.submission.SubmissionRepository
import com.kinetix.regulatory.submission.SubmissionStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedSubmissionRepository(private val db: Database? = null) : SubmissionRepository {

    override suspend fun save(submission: RegulatorySubmission): Unit = newSuspendedTransaction(db = db) {
        val existing = SubmissionsTable
            .selectAll()
            .where { SubmissionsTable.id eq submission.id }
            .firstOrNull()

        if (existing != null) {
            SubmissionsTable.update({ SubmissionsTable.id eq submission.id }) {
                it[status] = submission.status.name
                it[approverId] = submission.approverId
                it[submittedAt] = submission.submittedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
                it[acknowledgedAt] = submission.acknowledgedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
            }
        } else {
            SubmissionsTable.insert {
                it[id] = submission.id
                it[reportType] = submission.reportType
                it[status] = submission.status.name
                it[preparerId] = submission.preparerId
                it[approverId] = submission.approverId
                it[deadline] = OffsetDateTime.ofInstant(submission.deadline, ZoneOffset.UTC)
                it[submittedAt] = submission.submittedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
                it[acknowledgedAt] = submission.acknowledgedAt?.let { ts ->
                    OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)
                }
                it[createdAt] = OffsetDateTime.ofInstant(submission.createdAt, ZoneOffset.UTC)
            }
        }
    }

    override suspend fun findById(id: String): RegulatorySubmission? = newSuspendedTransaction(db = db) {
        SubmissionsTable
            .selectAll()
            .where { SubmissionsTable.id eq id }
            .map { it.toSubmission() }
            .firstOrNull()
    }

    override suspend fun findAll(): List<RegulatorySubmission> = newSuspendedTransaction(db = db) {
        SubmissionsTable
            .selectAll()
            .orderBy(SubmissionsTable.createdAt, SortOrder.DESC)
            .map { it.toSubmission() }
    }

    private fun ResultRow.toSubmission() = RegulatorySubmission(
        id = this[SubmissionsTable.id],
        reportType = this[SubmissionsTable.reportType],
        status = SubmissionStatus.valueOf(this[SubmissionsTable.status]),
        preparerId = this[SubmissionsTable.preparerId],
        approverId = this[SubmissionsTable.approverId],
        deadline = this[SubmissionsTable.deadline].toInstant(),
        submittedAt = this[SubmissionsTable.submittedAt]?.toInstant(),
        acknowledgedAt = this[SubmissionsTable.acknowledgedAt]?.toInstant(),
        createdAt = this[SubmissionsTable.createdAt].toInstant(),
    )
}
