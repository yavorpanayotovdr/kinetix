package com.kinetix.risk.client

import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeThresholds

data class RegimeDetectionResult(
    val regime: MarketRegime,
    val confidence: Double,
    val isConfirmed: Boolean,
    val consecutiveObservations: Int,
    val degradedInputs: Boolean,
    val earlyWarnings: List<EarlyWarningResult>,
    val correlationAnomalyScore: Double,
)

data class EarlyWarningResult(
    val signalName: String,
    val currentValue: Double,
    val threshold: Double,
    val proximityPct: Double,
    val message: String,
)

interface RegimeDetectorClient {
    suspend fun detectRegime(
        signals: RegimeSignals,
        thresholds: RegimeThresholds,
        currentRegime: MarketRegime,
        consecutiveObservations: Int,
        escalationDebounce: Int,
        deEscalationDebounce: Int,
    ): RegimeDetectionResult
}
