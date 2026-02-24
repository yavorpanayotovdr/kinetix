package com.kinetix.rates.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestForwardCurveRequest(
    val instrumentId: String,
    val assetClass: String,
    val points: List<CurvePointDto>,
    val source: String,
)
