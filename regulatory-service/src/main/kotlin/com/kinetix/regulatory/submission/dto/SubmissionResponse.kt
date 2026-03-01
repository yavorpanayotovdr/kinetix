package com.kinetix.regulatory.submission.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubmissionResponse(
    val id: String,
    val reportType: String,
    val status: String,
    val preparerId: String,
    val approverId: String?,
    val deadline: String,
    val submittedAt: String?,
    val acknowledgedAt: String?,
    val createdAt: String,
)
