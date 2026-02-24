package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ForwardCurveResponse(
    val instrumentId: String,
    val assetClass: String,
    val points: List<CurvePointDto>,
    val asOfDate: String,
    val source: String,
)
