package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

@Serializable
data class PositionBreakdownItem(
    val instrumentId: String,
    val instrumentName: String? = null,
    val assetClass: String,
    val marketValue: String,
    val varContribution: String,
    val percentageOfTotal: String,
    val delta: String? = null,
    val gamma: String? = null,
    val vega: String? = null,
    val quantity: String? = null,
    val instrumentType: String? = null,
)
