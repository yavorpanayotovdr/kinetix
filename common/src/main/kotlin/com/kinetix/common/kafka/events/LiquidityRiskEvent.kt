package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Kafka event schema for liquidity risk results on the `liquidity.risk.results` topic.
 *
 * Published by risk-orchestrator after each liquidity risk calculation.
 * Consumers (notification-service, UI via gateway) can subscribe to receive
 * near-real-time liquidity concentration alerts.
 */
@Serializable
data class LiquidityRiskEvent(
    val bookId: String,
    val portfolioLvar: String,
    val dataCompleteness: Double,
    val portfolioConcentrationStatus: String,
    val calculatedAt: String,
    val positionRisks: List<PositionLiquidityRiskItem> = emptyList(),
    val correlationId: String? = null,
)

@Serializable
data class PositionLiquidityRiskItem(
    val instrumentId: String,
    val assetClass: String,
    val tier: String,
    val horizonDays: Int,
    val advMissing: Boolean,
    val advStale: Boolean,
    val concentrationStatus: String,
    val lvarContribution: String,
)
