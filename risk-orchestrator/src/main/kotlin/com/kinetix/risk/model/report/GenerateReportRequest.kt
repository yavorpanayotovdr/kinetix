package com.kinetix.risk.model.report

data class GenerateReportRequest(
    val templateId: String,
    val bookId: String,
    val date: String?,
    val format: ReportFormat,
)
