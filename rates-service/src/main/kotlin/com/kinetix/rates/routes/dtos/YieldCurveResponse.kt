package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class YieldCurveResponse(
    val curveId: String,
    val currency: String,
    val tenors: List<TenorDto>,
    val asOfDate: String,
    val source: String,
)
