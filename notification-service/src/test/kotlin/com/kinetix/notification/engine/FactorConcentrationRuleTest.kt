package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.ConcentrationItem
import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.engine.extractors.FactorConcentrationExtractor
import com.kinetix.notification.model.AlertRule
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.ComparisonOperator
import com.kinetix.notification.model.DeliveryChannel
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun factorConcentrationEvent(bookId: String = "BOOK-1") = RiskResultEvent(
    bookId = bookId,
    varValue = "50000.0",
    expectedShortfall = "62500.0",
    calculationType = "PARAMETRIC",
    calculatedAt = "2026-03-24T10:00:00Z",
    concentrationByInstrument = listOf(
        ConcentrationItem(
            instrumentId = FactorConcentrationExtractor.FACTOR_CONCENTRATION_SENTINEL_ID,
            percentage = 100.0,
        )
    ),
)

private fun normalRiskEvent(bookId: String = "BOOK-1") = RiskResultEvent(
    bookId = bookId,
    varValue = "50000.0",
    expectedShortfall = "62500.0",
    calculationType = "PARAMETRIC",
    calculatedAt = "2026-03-24T10:00:00Z",
)

private fun factorConcentrationRule() = AlertRule(
    id = "fc-1",
    name = "Factor Concentration Warning",
    type = AlertType.FACTOR_CONCENTRATION,
    threshold = 0.5,
    operator = ComparisonOperator.GREATER_THAN,
    severity = Severity.WARNING,
    channels = listOf(DeliveryChannel.IN_APP),
)

class FactorConcentrationRuleTest : FunSpec({

    test("FACTOR_CONCENTRATION rule fires when factor concentration sentinel is present in event") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(factorConcentrationRule())

        val alerts = engine.evaluate(factorConcentrationEvent())

        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.FACTOR_CONCENTRATION
        alerts[0].severity shouldBe Severity.WARNING
        alerts[0].bookId shouldBe "BOOK-1"
    }

    test("FACTOR_CONCENTRATION rule does not fire for a normal risk event without sentinel") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(factorConcentrationRule())

        val alerts = engine.evaluate(normalRiskEvent())

        alerts.shouldBeEmpty()
    }

    test("FACTOR_CONCENTRATION rule does not fire when concentrationByInstrument is absent") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(factorConcentrationRule())

        val event = RiskResultEvent(
            bookId = "BOOK-1",
            varValue = "50000.0",
            expectedShortfall = "62500.0",
            calculationType = "PARAMETRIC",
            calculatedAt = "2026-03-24T10:00:00Z",
            concentrationByInstrument = null,
        )

        val alerts = engine.evaluate(event)

        alerts.shouldBeEmpty()
    }

    test("FactorConcentrationExtractor extracts 1.0 when sentinel is present") {
        val extractor = FactorConcentrationExtractor()
        val event = factorConcentrationEvent()

        extractor.extract(event) shouldBe 1.0
    }

    test("FactorConcentrationExtractor returns null when sentinel is absent") {
        val extractor = FactorConcentrationExtractor()
        val event = normalRiskEvent()

        extractor.extract(event) shouldBe null
    }

    test("FACTOR_CONCENTRATION rule fires independently of other alert types") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(factorConcentrationRule())
        engine.addRule(
            AlertRule(
                id = "var-1",
                name = "VaR Breach",
                type = AlertType.VAR_BREACH,
                threshold = 100_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP),
            )
        )

        // Factor concentration event with VaR below threshold
        val alerts = engine.evaluate(factorConcentrationEvent())

        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.FACTOR_CONCENTRATION
    }
})
