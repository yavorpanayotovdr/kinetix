package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkDetailResponse(
    val benchmarkId: String,
    val name: String,
    val description: String?,
    val createdAt: String,
    val constituents: List<BenchmarkConstituentResponse>,
)
