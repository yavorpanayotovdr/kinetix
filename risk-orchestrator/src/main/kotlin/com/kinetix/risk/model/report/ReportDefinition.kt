package com.kinetix.risk.model.report

data class ReportDefinition(
    val description: String,
    val source: String,
    val columns: List<String>,
)
