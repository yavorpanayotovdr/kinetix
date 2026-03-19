package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CrossBookVaRResultResponse(
    val portfolioGroupId: String,
    val bookIds: List<String>,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val bookContributions: List<BookVaRContributionResponse>,
    val totalStandaloneVar: String,
    val diversificationBenefit: String,
    val calculatedAt: String,
)
