package com.kinetix.regulatory.submission.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateSubmissionRequest(
    val reportType: String,
    val preparerId: String,
    val deadline: String,
)
