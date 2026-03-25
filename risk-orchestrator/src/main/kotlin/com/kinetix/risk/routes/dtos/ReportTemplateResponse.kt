package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ReportTemplateResponse(
    val templateId: String,
    val name: String,
    val templateType: String,
    val ownerUserId: String,
    val description: String,
    val source: String,
    val columns: List<String>,
    val createdAt: String,
    val updatedAt: String,
)
