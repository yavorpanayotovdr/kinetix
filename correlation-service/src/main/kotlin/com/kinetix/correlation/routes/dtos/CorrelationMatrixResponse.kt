package com.kinetix.correlation.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CorrelationMatrixResponse(
    val labels: List<String>,
    val values: List<Double>,
    val windowDays: Int,
    val asOfDate: String,
    val method: String,
)
