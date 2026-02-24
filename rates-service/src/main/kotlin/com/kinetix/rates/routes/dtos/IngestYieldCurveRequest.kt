package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestYieldCurveRequest(
    val curveId: String,
    val currency: String,
    val tenors: List<TenorDto>,
    val source: String,
)
