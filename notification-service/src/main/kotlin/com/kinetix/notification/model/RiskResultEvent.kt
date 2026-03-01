package com.kinetix.notification.model

import kotlinx.serialization.Serializable

@Serializable
data class RiskResultEvent(
    val portfolioId: String,
    val varValue: Double,
    val expectedShortfall: Double,
    val calculationType: String,
    val calculatedAt: String,
    val correlationId: String? = null,
)
