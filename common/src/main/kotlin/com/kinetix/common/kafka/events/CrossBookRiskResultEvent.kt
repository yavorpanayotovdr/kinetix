package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Published to the `risk.cross-book-results` topic after an aggregated
 * cross-book VaR calculation completes. Contains the portfolio-level
 * diversified VaR and per-book Euler-allocated contributions.
 */
@Serializable
data class CrossBookRiskResultEvent(
    val portfolioGroupId: String,
    val bookIds: List<String>,
    val varValue: String,
    val expectedShortfall: String,
    val calculationType: String,
    val confidenceLevel: String,
    val componentBreakdown: List<ComponentBreakdownEvent> = emptyList(),
    val bookContributions: List<BookVaRContributionEvent> = emptyList(),
    val totalStandaloneVar: String,
    val diversificationBenefit: String,
    val calculatedAt: String,
    val correlationId: String? = null,
)
