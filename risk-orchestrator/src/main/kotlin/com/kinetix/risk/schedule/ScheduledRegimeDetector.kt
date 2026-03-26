package com.kinetix.risk.schedule

import com.kinetix.risk.client.RegimeDetectorClient
import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import com.kinetix.risk.model.RegimeThresholds
import com.kinetix.risk.service.RegimeParameterProvider
import com.kinetix.risk.service.RegimeTransitionListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Runs every [intervalMillis] milliseconds to detect market regime transitions.
 *
 * Owns the persistent debounce state (confirmed regime + consecutive observation count)
 * and delegates classification to the risk engine via [RegimeDetectorClient].
 *
 * On a confirmed transition:
 *  - Updates the active regime state
 *  - Notifies all registered [RegimeTransitionListener] instances
 *
 * Regime transitions are debounced:
 *  - Escalation (higher severity): [escalationDebounce] consecutive observations required
 *  - De-escalation: [deEscalationDebounce] consecutive observation required
 */
class ScheduledRegimeDetector(
    private val regimeDetectorClient: RegimeDetectorClient,
    private val signalProvider: suspend () -> RegimeSignals,
    private val parameterProvider: RegimeParameterProvider,
    private val listeners: List<RegimeTransitionListener> = emptyList(),
    private val thresholds: RegimeThresholds = RegimeThresholds.DEFAULT,
    private val escalationDebounce: Int = 3,
    private val deEscalationDebounce: Int = 1,
    private val intervalMillis: Long = 15 * 60 * 1000L,
    private val lock: DistributedLock = NoOpDistributedLock(),
) {
    private val logger = LoggerFactory.getLogger(ScheduledRegimeDetector::class.java)

    // Mutable debounce state — owned by this scheduler
    @Volatile private var confirmedRegime: MarketRegime = MarketRegime.NORMAL
    @Volatile private var pendingRegime: MarketRegime? = null
    @Volatile private var pendingCount: Int = 0

    private val _currentState = AtomicReference<RegimeState>(
        RegimeState(
            regime = MarketRegime.NORMAL,
            detectedAt = Instant.now(),
            confidence = 1.0,
            signals = RegimeSignals(realisedVol20d = 0.0, crossAssetCorrelation = 0.0),
            varParameters = parameterProvider.parametersFor(MarketRegime.NORMAL),
            consecutiveObservations = 0,
            isConfirmed = true,
            degradedInputs = false,
        )
    )

    val currentState: RegimeState get() = _currentState.get()

    suspend fun start() {
        logger.info(
            "Starting ScheduledRegimeDetector — interval={}ms, escalationDebounce={}, deEscalationDebounce={}",
            intervalMillis, escalationDebounce, deEscalationDebounce,
        )
        while (coroutineContext.isActive) {
            lock.withLock("scheduled-regime-detector", ttlSeconds = intervalMillis / 1000) {
            try {
                detect()
            } catch (e: Exception) {
                logger.error("Regime detection cycle failed", e)
            }
            } // end lock
            delay(intervalMillis)
        }
    }

    suspend fun detect() {
        val signals = signalProvider()
        val previousRegime = confirmedRegime

        // Compute effective consecutive_observations to pass to gRPC
        // The gRPC call is stateless; we pass in our current pending state
        val classified = classifyWithClient(signals)
        val incoming = classified.regime

        if (incoming == confirmedRegime) {
            // Stable — reset any pending counter
            pendingRegime = null
            pendingCount = 0
        } else if (incoming == pendingRegime) {
            pendingCount++
        } else {
            pendingRegime = incoming
            pendingCount = 1
        }

        val isEscalation = incoming.severity > confirmedRegime.severity
        val required = if (isEscalation) escalationDebounce else deEscalationDebounce

        val isConfirmed = incoming == confirmedRegime || pendingCount >= required

        if (pendingCount >= required && incoming != confirmedRegime) {
            // Transition confirmed
            val newParams = parameterProvider.parametersFor(incoming)
            val newState = RegimeState(
                regime = incoming,
                detectedAt = Instant.now(),
                confidence = classified.confidence,
                signals = signals,
                varParameters = newParams,
                consecutiveObservations = pendingCount,
                isConfirmed = true,
                degradedInputs = classified.degradedInputs,
            )
            val oldRegime = confirmedRegime
            confirmedRegime = incoming
            pendingRegime = null
            pendingCount = 0
            _currentState.set(newState)

            logger.info(
                "Regime transition confirmed: {} → {} (confidence={:.2f})",
                oldRegime, incoming, classified.confidence,
            )

            listeners.forEach { listener ->
                try {
                    listener.onRegimeTransition(from = oldRegime, to = incoming, state = newState)
                } catch (e: Exception) {
                    logger.error("Regime transition listener {} failed", listener::class.simpleName, e)
                }
            }
        } else {
            // Update state without transition
            val updatedState = RegimeState(
                regime = confirmedRegime,
                detectedAt = _currentState.get().detectedAt,
                confidence = classified.confidence,
                signals = signals,
                varParameters = _currentState.get().varParameters,
                consecutiveObservations = pendingCount,
                isConfirmed = isConfirmed && incoming == confirmedRegime,
                degradedInputs = classified.degradedInputs,
            )
            _currentState.set(updatedState)
        }
    }

    private suspend fun classifyWithClient(signals: RegimeSignals): ClassificationResult {
        val result = regimeDetectorClient.detectRegime(
            signals = signals,
            thresholds = thresholds,
            currentRegime = confirmedRegime,
            consecutiveObservations = pendingCount,
            escalationDebounce = escalationDebounce,
            deEscalationDebounce = deEscalationDebounce,
        )
        return ClassificationResult(
            regime = result.regime,
            confidence = result.confidence,
            degradedInputs = result.degradedInputs,
        )
    }

    private data class ClassificationResult(
        val regime: MarketRegime,
        val confidence: Double,
        val degradedInputs: Boolean,
    )
}
