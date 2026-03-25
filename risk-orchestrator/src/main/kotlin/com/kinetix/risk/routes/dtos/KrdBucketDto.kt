package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class KrdBucketDto(
    val tenorLabel: String,
    val tenorDays: Int,
    val dv01: String,
)
