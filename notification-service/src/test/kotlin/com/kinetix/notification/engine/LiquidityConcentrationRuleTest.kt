package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.engine.extractors.LiquidityConcentrationExtractor
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class LiquidityConcentrationRuleTest : FunSpec({

    // ---------------------------------------------------------------------------
    // LiquidityConcentrationExtractor — unit tests
    // ---------------------------------------------------------------------------

    test("LiquidityConcentrationExtractor returns 2.0 when status is BREACHED") {
        val extractor = LiquidityConcentrationExtractor()
        val event = baseEvent.copy(liquidityConcentrationStatus = "BREACHED")
        extractor.extract(event) shouldBe 2.0
    }

    test("LiquidityConcentrationExtractor returns 1.0 when status is WARNING") {
        val extractor = LiquidityConcentrationExtractor()
        val event = baseEvent.copy(liquidityConcentrationStatus = "WARNING")
        extractor.extract(event) shouldBe 1.0
    }

    test("LiquidityConcentrationExtractor returns 0.0 when status is OK") {
        val extractor = LiquidityConcentrationExtractor()
        val event = baseEvent.copy(liquidityConcentrationStatus = "OK")
        extractor.extract(event) shouldBe 0.0
    }

    test("LiquidityConcentrationExtractor returns null when status is absent") {
        val extractor = LiquidityConcentrationExtractor()
        val event = baseEvent.copy(liquidityConcentrationStatus = null)
        extractor.extract(event).shouldBeNull()
    }

    // ---------------------------------------------------------------------------
    // RulesEngine integration — LIQUIDITY_CONCENTRATION alert type
    // ---------------------------------------------------------------------------

    test("LIQUIDITY_CONCENTRATION alert fires when status is BREACHED and threshold is 1.5") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "lc-1",
                name = "Liquidity Concentration Breach",
                type = AlertType.LIQUIDITY_CONCENTRATION,
                threshold = 1.5,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = baseEvent.copy(liquidityConcentrationStatus = "BREACHED")

        val alerts = engine.evaluate(event)

        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.LIQUIDITY_CONCENTRATION
        alerts[0].severity shouldBe Severity.CRITICAL
        alerts[0].currentValue shouldBe 2.0
        alerts[0].threshold shouldBe 1.5
        alerts[0].bookId shouldBe "port-1"
    }

    test("LIQUIDITY_CONCENTRATION alert fires when status is WARNING and threshold is 0.5") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "lc-2",
                name = "Liquidity Concentration Warning",
                type = AlertType.LIQUIDITY_CONCENTRATION,
                threshold = 0.5,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = baseEvent.copy(liquidityConcentrationStatus = "WARNING")

        val alerts = engine.evaluate(event)

        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.LIQUIDITY_CONCENTRATION
        alerts[0].severity shouldBe Severity.WARNING
        alerts[0].currentValue shouldBe 1.0
    }

    test("LIQUIDITY_CONCENTRATION alert does not fire when status is OK") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "lc-3",
                name = "Liquidity Concentration Breach",
                type = AlertType.LIQUIDITY_CONCENTRATION,
                threshold = 1.5,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = baseEvent.copy(liquidityConcentrationStatus = "OK")

        val alerts = engine.evaluate(event)

        alerts.shouldBeEmpty()
    }

    test("LIQUIDITY_CONCENTRATION alert does not fire when liquidityConcentrationStatus is absent") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "lc-4",
                name = "Liquidity Concentration Breach",
                type = AlertType.LIQUIDITY_CONCENTRATION,
                threshold = 1.5,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = baseEvent.copy(liquidityConcentrationStatus = null)

        val alerts = engine.evaluate(event)

        alerts.shouldBeEmpty()
    }
}) {
    companion object {
        private val baseEvent = RiskResultEvent(
            bookId = "port-1",
            varValue = "100000.0",
            expectedShortfall = "130000.0",
            calculationType = "PARAMETRIC",
            calculatedAt = "2026-03-24T10:00:00Z",
        )
    }
}
