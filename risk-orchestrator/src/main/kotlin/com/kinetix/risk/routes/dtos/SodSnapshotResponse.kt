package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SodSnapshotResponse(
    val portfolioId: String,
    val baselineDate: String,
    val snapshotType: String,
    val createdAt: String,
    val snapshotCount: Int,
)
