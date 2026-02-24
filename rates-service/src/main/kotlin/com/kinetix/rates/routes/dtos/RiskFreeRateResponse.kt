package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RiskFreeRateResponse(
    val currency: String,
    val tenor: String,
    val rate: String,
    val asOfDate: String,
    val source: String,
)
