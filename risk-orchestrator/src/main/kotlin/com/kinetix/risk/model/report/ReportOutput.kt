package com.kinetix.risk.model.report

import kotlinx.serialization.json.JsonArray
import java.time.Instant

data class ReportOutput(
    val outputId: String,
    val templateId: String,
    val generatedAt: Instant,
    val outputFormat: ReportFormat,
    val rowCount: Int,
    val outputData: JsonArray?,
)
