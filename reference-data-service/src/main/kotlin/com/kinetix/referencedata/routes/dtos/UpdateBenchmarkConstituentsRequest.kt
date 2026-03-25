package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class UpdateBenchmarkConstituentsRequest(
    val asOfDate: String,
    val constituents: List<ConstituentWeightDto>,
)

@Serializable
data class ConstituentWeightDto(
    val instrumentId: String,
    val weight: String,
)
