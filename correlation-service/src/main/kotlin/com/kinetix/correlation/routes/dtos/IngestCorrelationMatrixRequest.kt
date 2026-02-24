package com.kinetix.correlation.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestCorrelationMatrixRequest(
    val labels: List<String>,
    val values: List<Double>,
    val windowDays: Int,
    val method: String,
)
