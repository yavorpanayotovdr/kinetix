package com.kinetix.risk.kafka

import com.kinetix.common.kafka.events.MarketRegimeEvent
import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import io.mockk.mockk
import io.mockk.slot
import io.mockk.every
import java.time.Instant
import java.util.concurrent.CompletableFuture

private fun crisisState() = RegimeState(
    regime = MarketRegime.CRISIS,
    detectedAt = Instant.parse("2026-03-24T14:30:00Z"),
    confidence = 0.87,
    signals = RegimeSignals(
        realisedVol20d = 0.28,
        crossAssetCorrelation = 0.80,
        creditSpreadBps = 220.0,
        pnlVolatility = 0.07,
    ),
    varParameters = AdaptiveVaRParameters(
        calculationType = CalculationType.MONTE_CARLO,
        confidenceLevel = ConfidenceLevel.CL_99,
        timeHorizonDays = 5,
        correlationMethod = "stressed",
        numSimulations = 50_000,
    ),
    consecutiveObservations = 3,
    isConfirmed = true,
    degradedInputs = false,
)

class KafkaRegimeEventPublisherTest : FunSpec({

    fun makeProducer(): Pair<KafkaProducer<String, String>, KafkaRegimeEventPublisher> {
        val producer = mockk<KafkaProducer<String, String>>()
        val publisher = KafkaRegimeEventPublisher(producer)
        return producer to publisher
    }

    test("publishes regime transition with correct topic and partition key") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.NORMAL, to = crisisState())

        capturedRecord.captured.topic() shouldBe "risk.regime.changes"
        capturedRecord.captured.key() shouldBe "regime"
    }

    test("serialises regime and previousRegime correctly") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.NORMAL, to = crisisState())

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.regime shouldBe "CRISIS"
        event.previousRegime shouldBe "NORMAL"
    }

    test("serialises all signal fields including optional ones") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.ELEVATED_VOL, to = crisisState())

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.realisedVol20d shouldBe "0.28"
        event.crossAssetCorrelation shouldBe "0.8"
        event.creditSpreadBps shouldBe "220.0"
        event.pnlVolatility shouldBe "0.07"
    }

    test("serialises null optional signal fields as null in the event") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        val degradedState = crisisState().copy(
            signals = crisisState().signals.copy(creditSpreadBps = null, pnlVolatility = null),
            degradedInputs = true,
        )
        publisher.publish(from = MarketRegime.NORMAL, to = degradedState)

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.creditSpreadBps.shouldBeNull()
        event.pnlVolatility.shouldBeNull()
        event.degradedInputs shouldBe true
    }

    test("serialises effective VaR parameters from the target regime state") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.NORMAL, to = crisisState())

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.effectiveCalculationType shouldBe "MONTE_CARLO"
        event.effectiveConfidenceLevel shouldBe "CL_99"
        event.effectiveTimeHorizonDays shouldBe 5
        event.effectiveCorrelationMethod shouldBe "stressed"
        event.effectiveNumSimulations shouldBe 50_000
    }

    test("propagates correlationId when provided") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.NORMAL, to = crisisState(), correlationId = "corr-123")

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.correlationId shouldBe "corr-123"
    }

    test("correlationId is null when not provided") {
        val (producer, publisher) = makeProducer()
        val capturedRecord = slot<ProducerRecord<String, String>>()
        every { producer.send(capture(capturedRecord)) } returns CompletableFuture.completedFuture(mockk())

        publisher.publish(from = MarketRegime.NORMAL, to = crisisState())

        val event = Json.decodeFromString<MarketRegimeEvent>(capturedRecord.captured.value())
        event.correlationId.shouldBeNull()
    }
})
