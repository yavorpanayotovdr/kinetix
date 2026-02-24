package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VaRResultResponse(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
)
