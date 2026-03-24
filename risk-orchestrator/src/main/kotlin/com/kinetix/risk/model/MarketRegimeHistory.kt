package com.kinetix.risk.model

import java.time.Instant
import java.util.UUID

data class MarketRegimeHistory(
    val id: UUID,
    val regime: MarketRegime,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationMs: Long?,
    val signals: RegimeSignals,
    val varParameters: AdaptiveVaRParameters,
    val confidence: Double,
    val degradedInputs: Boolean,
    val consecutiveObservations: Int,
)
