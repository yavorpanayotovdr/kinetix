package com.kinetix.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
data class VolSurfaceDiffResponse(
    val instrumentId: String,
    val baseDate: String,
    val compareDate: String,
    val diffs: List<VolPointDiffResponse>,
)
