package com.kinetix.audit.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GapDetectionResponse(
    val gapCount: Int,
    val gaps: List<SequenceGap>,
)
