package com.kinetix.risk.schedule

import com.kinetix.risk.client.EarlyWarningResult
import com.kinetix.risk.client.RegimeDetectionResult
import com.kinetix.risk.client.RegimeDetectorClient
import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import com.kinetix.risk.model.RegimeThresholds
import com.kinetix.risk.service.AdaptiveRegimeParameterProvider
import com.kinetix.risk.service.RegimeTransitionListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ScheduledRegimeDetectorTest : FunSpec({

    fun crisisResult() = RegimeDetectionResult(
        regime = MarketRegime.CRISIS,
        confidence = 0.85,
        isConfirmed = true,
        consecutiveObservations = 3,
        degradedInputs = false,
        earlyWarnings = emptyList(),
        correlationAnomalyScore = 0.5,
    )

    fun normalResult() = RegimeDetectionResult(
        regime = MarketRegime.NORMAL,
        confidence = 0.92,
        isConfirmed = true,
        consecutiveObservations = 0,
        degradedInputs = false,
        earlyWarnings = emptyList(),
        correlationAnomalyScore = 0.1,
    )

    fun elevatedResult() = RegimeDetectionResult(
        regime = MarketRegime.ELEVATED_VOL,
        confidence = 0.70,
        isConfirmed = false,
        consecutiveObservations = 2,
        degradedInputs = false,
        earlyWarnings = emptyList(),
        correlationAnomalyScore = 0.2,
    )

    fun crisisSignals() = RegimeSignals(realisedVol20d = 0.30, crossAssetCorrelation = 0.80)
    fun normalSignals() = RegimeSignals(realisedVol20d = 0.10, crossAssetCorrelation = 0.40)
    fun elevatedSignals() = RegimeSignals(realisedVol20d = 0.20, crossAssetCorrelation = 0.50)

    val paramProvider = AdaptiveRegimeParameterProvider()

    test("initial regime is NORMAL with parametric VaR parameters") {
        val client = mockk<RegimeDetectorClient>()
        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { normalSignals() },
            parameterProvider = paramProvider,
        )
        detector.currentState.regime shouldBe MarketRegime.NORMAL
        detector.currentState.varParameters.calculationType shouldBe CalculationType.PARAMETRIC
    }

    test("escalation to CRISIS is confirmed after 3 consecutive observations") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns crisisResult()

        val captured = mutableListOf<MarketRegime>()
        val listener = object : RegimeTransitionListener {
            override suspend fun onRegimeTransition(from: MarketRegime, to: MarketRegime, state: RegimeState) {
                captured.add(to)
            }
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            listeners = listOf(listener),
            escalationDebounce = 3,
            deEscalationDebounce = 1,
        )

        detector.detect()  // obs 1 — pending
        detector.detect()  // obs 2 — pending
        detector.detect()  // obs 3 — confirmed

        detector.currentState.regime shouldBe MarketRegime.CRISIS
        detector.currentState.isConfirmed shouldBe true
        captured shouldBe listOf(MarketRegime.CRISIS)
    }

    test("single observation does not trigger escalation to crisis") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns crisisResult()

        val transitions = mutableListOf<MarketRegime>()
        val listener = object : RegimeTransitionListener {
            override suspend fun onRegimeTransition(from: MarketRegime, to: MarketRegime, state: RegimeState) {
                transitions.add(to)
            }
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            listeners = listOf(listener),
            escalationDebounce = 3,
        )

        detector.detect()  // only 1 observation

        detector.currentState.regime shouldBe MarketRegime.NORMAL
        transitions shouldBe emptyList()
    }

    test("oscillation resets debounce counter") {
        val client = mockk<RegimeDetectorClient>()
        val responses = mutableListOf(crisisResult(), crisisResult(), normalResult(), crisisResult())
        var callIdx = 0
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } answers {
            responses[callIdx++ % responses.size]
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            escalationDebounce = 3,
        )

        detector.detect()  // crisis obs 1
        detector.detect()  // crisis obs 2
        detector.detect()  // normal — resets counter
        detector.detect()  // crisis obs 1 again

        // Still NORMAL — counter was reset
        detector.currentState.regime shouldBe MarketRegime.NORMAL
    }

    test("de-escalation from CRISIS to NORMAL requires only 1 observation") {
        val client = mockk<RegimeDetectorClient>()
        val responses = mutableListOf(
            crisisResult(), crisisResult(), crisisResult(),  // escalate to CRISIS
            normalResult(),  // de-escalate
        )
        var callIdx = 0
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } answers {
            responses[callIdx++ % responses.size]
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { normalSignals() },
            parameterProvider = paramProvider,
            escalationDebounce = 3,
            deEscalationDebounce = 1,
        )

        detector.detect()
        detector.detect()
        detector.detect()  // now in CRISIS
        detector.currentState.regime shouldBe MarketRegime.CRISIS

        detector.detect()  // single normal observation → de-escalate
        detector.currentState.regime shouldBe MarketRegime.NORMAL
    }

    test("CRISIS regime sets Monte Carlo VaR parameters on transition") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns crisisResult()

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            escalationDebounce = 3,
        )

        repeat(3) { detector.detect() }

        val params = detector.currentState.varParameters
        params.calculationType shouldBe CalculationType.MONTE_CARLO
        params.confidenceLevel shouldBe ConfidenceLevel.CL_99
        params.timeHorizonDays shouldBe 5
        params.correlationMethod shouldBe "stressed"
        params.numSimulations shouldBe 50_000
    }

    test("regime transition listener receives correct from and to regime") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns crisisResult()

        var capturedFrom: MarketRegime? = null
        var capturedTo: MarketRegime? = null
        val listener = object : RegimeTransitionListener {
            override suspend fun onRegimeTransition(from: MarketRegime, to: MarketRegime, state: RegimeState) {
                capturedFrom = from
                capturedTo = to
            }
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            listeners = listOf(listener),
            escalationDebounce = 3,
        )

        repeat(3) { detector.detect() }

        capturedFrom shouldBe MarketRegime.NORMAL
        capturedTo shouldBe MarketRegime.CRISIS
    }

    test("listener failure does not abort detection cycle") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns crisisResult()

        val failingListener = object : RegimeTransitionListener {
            override suspend fun onRegimeTransition(from: MarketRegime, to: MarketRegime, state: RegimeState) {
                throw RuntimeException("listener exploded")
            }
        }

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { crisisSignals() },
            parameterProvider = paramProvider,
            listeners = listOf(failingListener),
            escalationDebounce = 3,
        )

        // Should not throw even if listener fails
        repeat(3) { detector.detect() }

        detector.currentState.regime shouldBe MarketRegime.CRISIS
    }

    test("degraded signals are surfaced in regime state") {
        val client = mockk<RegimeDetectorClient>()
        coEvery { client.detectRegime(any(), any(), any(), any(), any(), any()) } returns
            normalResult().copy(degradedInputs = true)

        val detector = ScheduledRegimeDetector(
            regimeDetectorClient = client,
            signalProvider = { RegimeSignals(realisedVol20d = 0.10, crossAssetCorrelation = 0.40) },
            parameterProvider = paramProvider,
        )

        detector.detect()
        detector.currentState.degradedInputs shouldBe true
    }
})
