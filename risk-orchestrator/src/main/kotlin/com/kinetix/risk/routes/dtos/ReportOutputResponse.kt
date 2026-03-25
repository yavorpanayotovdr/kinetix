package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class ReportOutputResponse(
    val outputId: String,
    val templateId: String,
    val generatedAt: String,
    val outputFormat: String,
    val rowCount: Int,
    val outputData: JsonArray?,
)
