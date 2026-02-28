package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SodBaselineStatusResponse(
    val exists: Boolean,
    val baselineDate: String? = null,
    val snapshotType: String? = null,
    val createdAt: String? = null,
)
