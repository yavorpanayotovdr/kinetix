package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ParameterDiffDto(
    val paramName: String,
    val baseValue: String?,
    val targetValue: String?,
)
