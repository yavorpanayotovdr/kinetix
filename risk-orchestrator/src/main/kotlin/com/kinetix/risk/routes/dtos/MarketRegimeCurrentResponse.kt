package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class AdaptiveVaRParametersDto(
    val calculationType: String,
    val confidenceLevel: String,
    val timeHorizonDays: Int,
    val correlationMethod: String,
    val numSimulations: Int? = null,
)

@Serializable
data class RegimeSignalsDto(
    val realisedVol20d: Double,
    val crossAssetCorrelation: Double,
    val creditSpreadBps: Double? = null,
    val pnlVolatility: Double? = null,
)

@Serializable
data class MarketRegimeCurrentResponse(
    val regime: String,
    val isConfirmed: Boolean,
    val confidence: Double,
    val consecutiveObservations: Int,
    val detectedAt: String,
    val degradedInputs: Boolean,
    val signals: RegimeSignalsDto,
    val varParameters: AdaptiveVaRParametersDto,
)
