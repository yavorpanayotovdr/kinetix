package com.kinetix.regulatory.submission.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApproveSubmissionRequest(
    val approverId: String,
)
