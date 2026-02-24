package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestRiskFreeRateRequest(
    val currency: String,
    val tenor: String,
    val rate: String,
    val source: String,
)
