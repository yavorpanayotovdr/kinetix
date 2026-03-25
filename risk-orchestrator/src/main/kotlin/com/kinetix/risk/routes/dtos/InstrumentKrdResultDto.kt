package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentKrdResultDto(
    val instrumentId: String,
    val krdBuckets: List<KrdBucketDto>,
    val totalDv01: String,
)
