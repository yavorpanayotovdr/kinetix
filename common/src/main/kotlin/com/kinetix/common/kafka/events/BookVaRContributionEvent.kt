package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Per-book contribution to an aggregated cross-book VaR calculation.
 * Nested within [CrossBookRiskResultEvent].
 */
@Serializable
data class BookVaRContributionEvent(
    val bookId: String,
    val varContribution: String,
    val percentageOfTotal: String,
    val standaloneVar: String,
    val diversificationBenefit: String,
    val marginalVar: String = "0.0",
    val incrementalVar: String = "0.0",
)
