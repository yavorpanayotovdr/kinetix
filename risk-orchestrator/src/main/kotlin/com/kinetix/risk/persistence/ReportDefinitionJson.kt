package com.kinetix.risk.persistence

import kotlinx.serialization.Serializable

@Serializable
data class ReportDefinitionJson(
    val description: String = "",
    val source: String = "",
    val columns: List<String> = emptyList(),
)
