package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class KeyRateDurationResponse(
    val bookId: String,
    val instruments: List<InstrumentKrdResultDto>,
    val aggregated: List<KrdBucketDto>,
)
