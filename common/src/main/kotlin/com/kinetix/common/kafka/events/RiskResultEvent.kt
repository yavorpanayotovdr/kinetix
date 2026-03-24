package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Canonical Kafka event schema for risk calculation results on the `risk.results` topic.
 *
 * This is the superset of all per-service definitions. Fields present only in the
 * risk-orchestrator producer (calculationType, confidenceLevel, componentBreakdown)
 * are made nullable/defaulted so that consumers that do not need them can deserialize
 * with `ignoreUnknownKeys = true` without error.
 */
@Serializable
data class RiskResultEvent(
    val bookId: String,
    val varValue: String,
    val expectedShortfall: String,
    val calculationType: String,
    val calculatedAt: String,
    val confidenceLevel: String = "",
    val componentBreakdown: List<ComponentBreakdownEvent> = emptyList(),
    val correlationId: String? = null,
    val positionBreakdown: List<PositionBreakdownItem>? = null,
    val aggregateDelta: String? = null,
    val aggregateVega: String? = null,
    val marginUtilisation: Double? = null,
    val concentrationByInstrument: List<ConcentrationItem>? = null,
    val liquidityConcentrationStatus: String? = null,
)

@Serializable
data class ConcentrationItem(
    val instrumentId: String,
    val percentage: Double,
)
