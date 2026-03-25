package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ReportGenerateRequestBody(
    val templateId: String? = null,
    val bookId: String? = null,
    val date: String? = null,
    val format: String = "JSON",
)
