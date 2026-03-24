package com.kinetix.risk.model

import java.time.Instant

data class RegimeSignals(
    val realisedVol20d: Double,
    val crossAssetCorrelation: Double,
    val creditSpreadBps: Double? = null,
    val pnlVolatility: Double? = null,
)

data class AdaptiveVaRParameters(
    val calculationType: CalculationType,
    val confidenceLevel: ConfidenceLevel,
    val timeHorizonDays: Int,
    val correlationMethod: String,
    val numSimulations: Int? = null,
)

data class RegimeState(
    val regime: MarketRegime,
    val detectedAt: Instant,
    val confidence: Double,
    val signals: RegimeSignals,
    val varParameters: AdaptiveVaRParameters,
    val consecutiveObservations: Int,
    val isConfirmed: Boolean,
    val degradedInputs: Boolean,
)
