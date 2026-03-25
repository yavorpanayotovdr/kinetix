package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkConstituentResponse(
    val instrumentId: String,
    val weight: String,
    val asOfDate: String,
)
