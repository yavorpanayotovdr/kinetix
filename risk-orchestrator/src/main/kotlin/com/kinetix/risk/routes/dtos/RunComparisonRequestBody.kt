package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RunComparisonRequestBody(
    val baseJobId: String? = null,
    val targetJobId: String? = null,
    val baseDate: String? = null,
    val targetDate: String? = null,
)
