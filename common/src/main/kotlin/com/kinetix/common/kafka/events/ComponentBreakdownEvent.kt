package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Canonical Kafka event schema for per-asset-class VaR breakdown within a [RiskResultEvent].
 */
@Serializable
data class ComponentBreakdownEvent(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)
