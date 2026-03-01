package com.kinetix.regulatory.submission

import java.time.Instant
import java.util.UUID

class SubmissionService(private val repository: SubmissionRepository) {

    suspend fun create(reportType: String, preparerId: String, deadline: Instant): RegulatorySubmission {
        val submission = RegulatorySubmission(
            id = UUID.randomUUID().toString(),
            reportType = reportType,
            status = SubmissionStatus.DRAFT,
            preparerId = preparerId,
            approverId = null,
            deadline = deadline,
            submittedAt = null,
            acknowledgedAt = null,
            createdAt = Instant.now(),
        )
        repository.save(submission)
        return submission
    }

    suspend fun submitForReview(id: String): RegulatorySubmission {
        val submission = findOrThrow(id)
        if (submission.status != SubmissionStatus.DRAFT) {
            throw IllegalStateException("Can only submit for review from DRAFT status, current: ${submission.status}")
        }
        val updated = submission.copy(status = SubmissionStatus.PENDING_REVIEW)
        repository.save(updated)
        return updated
    }

    suspend fun approve(id: String, approverId: String): RegulatorySubmission {
        val submission = findOrThrow(id)
        if (submission.status != SubmissionStatus.PENDING_REVIEW) {
            throw IllegalStateException("Can only approve from PENDING_REVIEW status, current: ${submission.status}")
        }
        if (submission.preparerId == approverId) {
            throw IllegalArgumentException("Approver cannot be the same as preparer (four-eyes principle)")
        }
        val updated = submission.copy(
            status = SubmissionStatus.APPROVED,
            approverId = approverId,
        )
        repository.save(updated)
        return updated
    }

    suspend fun submit(id: String): RegulatorySubmission {
        val submission = findOrThrow(id)
        if (submission.status != SubmissionStatus.APPROVED) {
            throw IllegalStateException("Can only submit from APPROVED status, current: ${submission.status}")
        }
        val updated = submission.copy(
            status = SubmissionStatus.SUBMITTED,
            submittedAt = Instant.now(),
        )
        repository.save(updated)
        return updated
    }

    suspend fun listAll(): List<RegulatorySubmission> = repository.findAll()

    suspend fun findById(id: String): RegulatorySubmission? = repository.findById(id)

    private suspend fun findOrThrow(id: String): RegulatorySubmission =
        repository.findById(id) ?: throw NoSuchElementException("Submission not found: $id")
}
