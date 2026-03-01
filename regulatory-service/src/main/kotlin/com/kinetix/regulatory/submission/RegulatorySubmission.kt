package com.kinetix.regulatory.submission

import java.time.Instant

data class RegulatorySubmission(
    val id: String,
    val reportType: String,
    val status: SubmissionStatus,
    val preparerId: String,
    val approverId: String?,
    val deadline: Instant,
    val submittedAt: Instant?,
    val acknowledgedAt: Instant?,
    val createdAt: Instant,
)
