package com.kinetix.regulatory.historical.dto

import kotlinx.serialization.Serializable

@Serializable
data class HistoricalScenarioPeriodResponse(
    val periodId: String,
    val name: String,
    val description: String?,
    val startDate: String,
    val endDate: String,
    val assetClassFocus: String?,
    val severityLabel: String?,
)
