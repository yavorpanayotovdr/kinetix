package com.kinetix.audit.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SequenceGap(
    val afterSequence: Long,
    val beforeSequence: Long,
    val missingCount: Long,
)
