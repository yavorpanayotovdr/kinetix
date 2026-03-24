package com.kinetix.risk.client

import com.kinetix.proto.risk.MLPredictionServiceGrpcKt
import com.kinetix.proto.risk.RegimeDetectionRequest
import com.kinetix.proto.risk.RegimeSignalsProto
import com.kinetix.proto.risk.RegimeThresholdsProto
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeThresholds
import org.slf4j.LoggerFactory

class GrpcRegimeDetectorClient(
    private val stub: MLPredictionServiceGrpcKt.MLPredictionServiceCoroutineStub,
    private val deadlineMs: Long = 30_000,
) : RegimeDetectorClient {

    private val logger = LoggerFactory.getLogger(GrpcRegimeDetectorClient::class.java)

    override suspend fun detectRegime(
        signals: RegimeSignals,
        thresholds: RegimeThresholds,
        currentRegime: MarketRegime,
        consecutiveObservations: Int,
        escalationDebounce: Int,
        deEscalationDebounce: Int,
    ): RegimeDetectionResult {
        val signalsProto = RegimeSignalsProto.newBuilder().apply {
            realisedVol20D = signals.realisedVol20d
            crossAssetCorrelation = signals.crossAssetCorrelation
            if (signals.creditSpreadBps != null) {
                creditSpreadBps = signals.creditSpreadBps
                creditSpreadPresent = true
            }
            if (signals.pnlVolatility != null) {
                pnlVolatility = signals.pnlVolatility
                pnlVolatilityPresent = true
            }
        }.build()

        val thresholdsProto = RegimeThresholdsProto.newBuilder().apply {
            normalVolCeiling = thresholds.normalVolCeiling
            elevatedVolCeiling = thresholds.elevatedVolCeiling
            crisisCorrelationFloor = thresholds.crisisCorrelationFloor
        }.build()

        val request = RegimeDetectionRequest.newBuilder().apply {
            this.signals = signalsProto
            this.thresholds = thresholdsProto
            this.currentRegime = currentRegime.name
            this.consecutiveObservations = consecutiveObservations
            this.escalationDebounce = escalationDebounce
            this.deEscalationDebounce = deEscalationDebounce
        }.build()

        val response = stub
            .withDeadlineAfter(deadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .detectRegime(request)

        val regime = try {
            MarketRegime.valueOf(response.regime)
        } catch (e: IllegalArgumentException) {
            logger.warn("Unknown regime '{}' returned by risk engine, defaulting to NORMAL", response.regime)
            MarketRegime.NORMAL
        }

        val warnings = response.earlyWarningsList.map { w ->
            EarlyWarningResult(
                signalName = w.signalName,
                currentValue = w.currentValue,
                threshold = w.threshold,
                proximityPct = w.proximityPct,
                message = w.message,
            )
        }

        return RegimeDetectionResult(
            regime = regime,
            confidence = response.confidence,
            isConfirmed = response.isConfirmed,
            consecutiveObservations = response.consecutiveObservations,
            degradedInputs = response.degradedInputs,
            earlyWarnings = warnings,
            correlationAnomalyScore = response.correlationAnomalyScore,
        )
    }
}
