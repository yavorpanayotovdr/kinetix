package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DependenciesRequestBody(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)
