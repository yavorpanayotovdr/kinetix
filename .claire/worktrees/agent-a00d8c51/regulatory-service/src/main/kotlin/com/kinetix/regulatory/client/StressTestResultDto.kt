package com.kinetix.regulatory.client

import kotlinx.serialization.Serializable

@Serializable
data class StressTestResultDto(
    val pnlImpact: String,
)
