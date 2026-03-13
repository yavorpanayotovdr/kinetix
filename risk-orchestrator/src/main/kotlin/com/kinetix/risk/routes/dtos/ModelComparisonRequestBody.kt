package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ModelComparisonRequestBody(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val targetCalculationType: String? = null,
    val targetConfidenceLevel: String? = null,
    val targetNumSimulations: Int? = null,
)
