package com.kinetix.regulatory.submission

interface SubmissionRepository {
    suspend fun save(submission: RegulatorySubmission)
    suspend fun findById(id: String): RegulatorySubmission?
    suspend fun findAll(): List<RegulatorySubmission>
}
