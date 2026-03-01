package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class RulesEngineTest : FunSpec({

    test("VaR breach triggers when value exceeds threshold") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.VAR_BREACH
        alerts[0].severity shouldBe Severity.CRITICAL
        alerts[0].currentValue shouldBe 150_000.0
        alerts[0].threshold shouldBe 100_000.0
        alerts[0].portfolioId shouldBe "port-1"
    }

    test("VaR breach does not trigger below threshold") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        val event = RiskResultEvent("port-1", "50000.0", "60000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts.shouldBeEmpty()
    }

    test("PnL threshold triggers correctly") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r2", name = "ES Warning", type = AlertType.PNL_THRESHOLD,
                threshold = 200_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING, channels = listOf(DeliveryChannel.EMAIL),
            ),
        )
        val event = RiskResultEvent("port-1", "100000.0", "250000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts shouldHaveSize 1
        alerts[0].type shouldBe AlertType.PNL_THRESHOLD
        alerts[0].currentValue shouldBe 250_000.0
    }

    test("disabled rule does not trigger") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
                enabled = false,
            ),
        )
        val event = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts.shouldBeEmpty()
    }

    test("multiple rules can fire simultaneously") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        engine.addRule(
            AlertRule(
                id = "r2", name = "ES Warning", type = AlertType.PNL_THRESHOLD,
                threshold = 200_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING, channels = listOf(DeliveryChannel.EMAIL),
            ),
        )
        val event = RiskResultEvent("port-1", "150000.0", "250000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts shouldHaveSize 2
    }

    test("add and remove rule") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        engine.listRules() shouldHaveSize 1
        engine.removeRule("r1")
        engine.listRules().shouldBeEmpty()
    }

    test("list rules returns all") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "Rule 1", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        engine.addRule(
            AlertRule(
                id = "r2", name = "Rule 2", type = AlertType.PNL_THRESHOLD,
                threshold = 200_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING, channels = listOf(DeliveryChannel.EMAIL),
            ),
        )
        val rules = engine.listRules()
        rules shouldHaveSize 2
    }

    test("empty rules returns no alerts") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val event = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val alerts = engine.evaluate(event)
        alerts.shouldBeEmpty()
    }
})
