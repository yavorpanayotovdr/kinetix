package com.kinetix.notification.model

import kotlinx.serialization.Serializable

@Serializable
data class RiskResultEvent(
    val portfolioId: String,
    val varValue: String,
    val expectedShortfall: String,
    val calculationType: String,
    val calculatedAt: String,
    val confidenceLevel: String = "",
    val correlationId: String? = null,
)
