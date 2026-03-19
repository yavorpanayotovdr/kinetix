package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class RunStressTestRequest(
    val portfolioId: String,
    val modelVersion: String? = null,
)
