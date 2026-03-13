package com.kinetix.risk.model

data class ParameterDiff(
    val paramName: String,
    val baseValue: String?,
    val targetValue: String?,
)
