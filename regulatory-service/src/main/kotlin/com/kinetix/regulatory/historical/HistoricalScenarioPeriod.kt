package com.kinetix.regulatory.historical

data class HistoricalScenarioPeriod(
    val periodId: String,
    val name: String,
    val description: String?,
    val startDate: String,
    val endDate: String,
    val assetClassFocus: String?,
    val severityLabel: String?,
)
