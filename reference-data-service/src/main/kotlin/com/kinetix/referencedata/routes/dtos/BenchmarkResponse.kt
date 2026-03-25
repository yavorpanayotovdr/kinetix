package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkResponse(
    val benchmarkId: String,
    val name: String,
    val description: String?,
    val createdAt: String,
)
