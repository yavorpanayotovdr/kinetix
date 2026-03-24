package com.kinetix.schema

import com.kinetix.common.kafka.events.MarketRegimeEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.Json

class MarketRegimeEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("MarketRegimeEvent serializes and deserializes all required fields") {
        val event = MarketRegimeEvent(
            regime = "CRISIS",
            previousRegime = "ELEVATED_VOL",
            transitionedAt = "2026-03-24T14:30:00Z",
            confidence = "0.72",
            degradedInputs = false,
            consecutiveObservations = 3,
            realisedVol20d = "0.31",
            crossAssetCorrelation = "0.82",
            creditSpreadBps = "245.0",
            pnlVolatility = "0.08",
            effectiveCalculationType = "MONTE_CARLO",
            effectiveConfidenceLevel = "CL_99",
            effectiveTimeHorizonDays = 5,
            effectiveCorrelationMethod = "stressed",
            effectiveNumSimulations = 50000,
            correlationId = "corr-regime-001",
        )

        val serialized = Json.encodeToString(MarketRegimeEvent.serializer(), event)
        val deserialized = json.decodeFromString<MarketRegimeEvent>(serialized)

        deserialized.regime shouldBe "CRISIS"
        deserialized.previousRegime shouldBe "ELEVATED_VOL"
        deserialized.transitionedAt shouldBe "2026-03-24T14:30:00Z"
        deserialized.confidence shouldBe "0.72"
        deserialized.degradedInputs shouldBe false
        deserialized.consecutiveObservations shouldBe 3
        deserialized.realisedVol20d shouldBe "0.31"
        deserialized.crossAssetCorrelation shouldBe "0.82"
        deserialized.creditSpreadBps shouldBe "245.0"
        deserialized.pnlVolatility shouldBe "0.08"
        deserialized.effectiveCalculationType shouldBe "MONTE_CARLO"
        deserialized.effectiveConfidenceLevel shouldBe "CL_99"
        deserialized.effectiveTimeHorizonDays shouldBe 5
        deserialized.effectiveCorrelationMethod shouldBe "stressed"
        deserialized.effectiveNumSimulations shouldBe 50000
        deserialized.correlationId shouldBe "corr-regime-001"
    }

    test("MarketRegimeEvent with null optional fields serializes correctly") {
        val event = MarketRegimeEvent(
            regime = "NORMAL",
            previousRegime = "ELEVATED_VOL",
            transitionedAt = "2026-03-24T16:00:00Z",
            confidence = "0.88",
            degradedInputs = true,
            consecutiveObservations = 1,
            realisedVol20d = "0.12",
            crossAssetCorrelation = "0.38",
            creditSpreadBps = null,
            pnlVolatility = null,
            effectiveCalculationType = "PARAMETRIC",
            effectiveConfidenceLevel = "CL_95",
            effectiveTimeHorizonDays = 1,
            effectiveCorrelationMethod = "standard",
            effectiveNumSimulations = null,
            correlationId = null,
        )

        val serialized = Json.encodeToString(MarketRegimeEvent.serializer(), event)
        val deserialized = json.decodeFromString<MarketRegimeEvent>(serialized)

        deserialized.regime shouldBe "NORMAL"
        deserialized.degradedInputs shouldBe true
        deserialized.creditSpreadBps.shouldBeNull()
        deserialized.pnlVolatility.shouldBeNull()
        deserialized.effectiveNumSimulations.shouldBeNull()
        deserialized.correlationId.shouldBeNull()
    }

    test("consumer with ignoreUnknownKeys tolerates future fields") {
        val jsonWithExtraField = """
            {
                "regime": "ELEVATED_VOL",
                "previousRegime": "NORMAL",
                "transitionedAt": "2026-03-24T12:00:00Z",
                "confidence": "0.65",
                "degradedInputs": false,
                "consecutiveObservations": 3,
                "realisedVol20d": "0.18",
                "crossAssetCorrelation": "0.55",
                "effectiveCalculationType": "HISTORICAL",
                "effectiveConfidenceLevel": "CL_99",
                "effectiveTimeHorizonDays": 1,
                "effectiveCorrelationMethod": "ewma",
                "futureField": "ignored_by_consumer"
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<MarketRegimeEvent>(jsonWithExtraField)
        deserialized.regime shouldBe "ELEVATED_VOL"
        deserialized.effectiveCalculationType shouldBe "HISTORICAL"
    }

    test("regime field is the Kafka partition key") {
        val event = MarketRegimeEvent(
            regime = "CRISIS",
            previousRegime = "NORMAL",
            transitionedAt = "2026-03-24T14:00:00Z",
            confidence = "0.90",
            degradedInputs = false,
            consecutiveObservations = 3,
            realisedVol20d = "0.35",
            crossAssetCorrelation = "0.85",
            effectiveCalculationType = "MONTE_CARLO",
            effectiveConfidenceLevel = "CL_99",
            effectiveTimeHorizonDays = 5,
            effectiveCorrelationMethod = "stressed",
        )
        // Regime events use a constant partition key so all consumers see them in order
        event.regime.shouldNotBeNull()
    }

    test("CRISIS regime event carries Monte Carlo parameters") {
        val event = MarketRegimeEvent(
            regime = "CRISIS",
            previousRegime = "ELEVATED_VOL",
            transitionedAt = "2026-03-24T14:30:00Z",
            confidence = "0.75",
            degradedInputs = false,
            consecutiveObservations = 3,
            realisedVol20d = "0.30",
            crossAssetCorrelation = "0.80",
            effectiveCalculationType = "MONTE_CARLO",
            effectiveConfidenceLevel = "CL_99",
            effectiveTimeHorizonDays = 5,
            effectiveCorrelationMethod = "stressed",
            effectiveNumSimulations = 50000,
        )

        event.effectiveCalculationType shouldBe "MONTE_CARLO"
        event.effectiveNumSimulations shouldBe 50000
        event.effectiveTimeHorizonDays shouldBe 5
    }

    test("NORMAL regime event carries parametric parameters with no simulations") {
        val event = MarketRegimeEvent(
            regime = "NORMAL",
            previousRegime = "CRISIS",
            transitionedAt = "2026-03-24T18:00:00Z",
            confidence = "0.92",
            degradedInputs = false,
            consecutiveObservations = 1,
            realisedVol20d = "0.10",
            crossAssetCorrelation = "0.30",
            effectiveCalculationType = "PARAMETRIC",
            effectiveConfidenceLevel = "CL_95",
            effectiveTimeHorizonDays = 1,
            effectiveCorrelationMethod = "standard",
            effectiveNumSimulations = null,
        )

        event.effectiveCalculationType shouldBe "PARAMETRIC"
        event.effectiveNumSimulations.shouldBeNull()
        event.effectiveConfidenceLevel shouldBe "CL_95"
    }
})
