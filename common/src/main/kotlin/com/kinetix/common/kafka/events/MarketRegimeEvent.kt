package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

/**
 * Kafka event published to `risk.regime.changes` when the market regime transitions.
 *
 * Published by risk-orchestrator after a confirmed regime transition.
 * Consumed by notification-service (regime change alerts) and the UI (via gateway WebSocket).
 *
 * Partition key: a constant ("regime") — single-partition ordering is required
 * because regime transitions must be processed in sequence.
 */
@Serializable
data class MarketRegimeEvent(
    /** The confirmed regime after this transition. One of: NORMAL, ELEVATED_VOL, CRISIS, RECOVERY. */
    val regime: String,
    /** The regime that was active before this transition. */
    val previousRegime: String,
    /** ISO-8601 instant when the transition was confirmed. */
    val transitionedAt: String,
    /** Confidence score in [0, 1] for the new regime classification. */
    val confidence: String,
    /** Whether this classification was produced with degraded (two-factor) signals. */
    val degradedInputs: Boolean,
    /** Number of consecutive observations that triggered the debounce confirmation. */
    val consecutiveObservations: Int,
    /** Realised 20-day annualised vol at time of transition. */
    val realisedVol20d: String,
    /** Average off-diagonal cross-asset correlation at time of transition. */
    val crossAssetCorrelation: String,
    /** Credit spread in bps at time of transition; null if unavailable. */
    val creditSpreadBps: String? = null,
    /** 20-day rolling P&L volatility at time of transition; null if unavailable. */
    val pnlVolatility: String? = null,
    /** VaR calculation type in effect after the transition (e.g. "MONTE_CARLO"). */
    val effectiveCalculationType: String,
    /** Confidence level in effect after the transition (e.g. "CL_99"). */
    val effectiveConfidenceLevel: String,
    /** Time horizon in days in effect after the transition. */
    val effectiveTimeHorizonDays: Int,
    /** Correlation method in effect after the transition. */
    val effectiveCorrelationMethod: String,
    /** Number of Monte Carlo simulations in effect (null for non-MC methods). */
    val effectiveNumSimulations: Int? = null,
    /** Correlation ID for distributed tracing. */
    val correlationId: String? = null,
)
