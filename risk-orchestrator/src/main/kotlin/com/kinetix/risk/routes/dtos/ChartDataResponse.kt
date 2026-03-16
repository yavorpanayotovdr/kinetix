package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ChartDataResponse(
    val points: List<ChartDataPointResponse>,
    val bucketSizeMs: Long,
)
