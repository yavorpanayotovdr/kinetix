package com.kinetix.risk.model.report

import java.time.Instant

data class ReportTemplate(
    val templateId: String,
    val name: String,
    val templateType: ReportTemplateType,
    val ownerUserId: String,
    val definition: ReportDefinition,
    val createdAt: Instant,
    val updatedAt: Instant,
)
