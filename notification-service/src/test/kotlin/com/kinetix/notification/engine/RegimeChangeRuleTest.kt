package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.MarketRegimeEvent
import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.AlertStatus
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun regimeEvent(
    regime: String,
    previousRegime: String = "NORMAL",
    confidence: String = "0.87",
) = MarketRegimeEvent(
    regime = regime,
    previousRegime = previousRegime,
    transitionedAt = "2026-03-24T14:30:00Z",
    confidence = confidence,
    degradedInputs = false,
    consecutiveObservations = 3,
    realisedVol20d = "0.28",
    crossAssetCorrelation = "0.8",
    effectiveCalculationType = "MONTE_CARLO",
    effectiveConfidenceLevel = "CL_99",
    effectiveTimeHorizonDays = 5,
    effectiveCorrelationMethod = "stressed",
    effectiveNumSimulations = 50_000,
)

class RegimeChangeRuleTest : FunSpec({

    test("fires CRITICAL alert when regime transitions to CRISIS") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("CRISIS"))

        alert.shouldNotBeNull()
        alert.type shouldBe AlertType.REGIME_CHANGE
        alert.severity shouldBe Severity.CRITICAL
        alert.status shouldBe AlertStatus.TRIGGERED
    }

    test("fires WARNING alert when regime transitions to ELEVATED_VOL") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("ELEVATED_VOL"))

        alert.shouldNotBeNull()
        alert.severity shouldBe Severity.WARNING
    }

    test("fires INFO alert when regime transitions to RECOVERY") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("RECOVERY", previousRegime = "CRISIS"))

        alert.shouldNotBeNull()
        alert.severity shouldBe Severity.INFO
    }

    test("returns null for NORMAL regime — no alert needed") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("NORMAL", previousRegime = "ELEVATED_VOL"))

        alert.shouldBeNull()
    }

    test("alert message includes regime and previous regime") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("CRISIS", previousRegime = "ELEVATED_VOL"))

        alert.shouldNotBeNull()
        alert.message shouldContain "CRISIS"
        alert.message shouldContain "ELEVATED_VOL"
    }

    test("alert message includes effective VaR method for actionable context") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("CRISIS"))

        alert.shouldNotBeNull()
        alert.message shouldContain "MONTE_CARLO"
    }

    test("alert carries correlationId when present in the event") {
        val rule = RegimeChangeRule()
        val event = regimeEvent("CRISIS").copy(correlationId = "corr-abc")

        val alert = rule.evaluate(event)

        alert.shouldNotBeNull()
        alert.correlationId shouldBe "corr-abc"
    }

    test("alert bookId is set to 'GLOBAL' since regime is portfolio-wide") {
        val rule = RegimeChangeRule()

        val alert = rule.evaluate(regimeEvent("CRISIS"))

        alert.shouldNotBeNull()
        alert.bookId shouldBe "GLOBAL"
    }
})
