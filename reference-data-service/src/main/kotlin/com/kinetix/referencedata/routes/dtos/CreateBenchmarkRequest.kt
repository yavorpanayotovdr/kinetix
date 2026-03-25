package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateBenchmarkRequest(
    val benchmarkId: String,
    val name: String,
    val description: String? = null,
)
