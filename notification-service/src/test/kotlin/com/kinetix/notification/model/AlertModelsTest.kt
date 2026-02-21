package com.kinetix.notification.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.time.Instant

class AlertModelsTest : FunSpec({

    test("AlertType has three values") {
        AlertType.entries.map { it.name } shouldContainExactly listOf(
            "VAR_BREACH", "PNL_THRESHOLD", "RISK_LIMIT",
        )
    }

    test("Severity has three levels") {
        Severity.entries.map { it.name } shouldContainExactly listOf(
            "INFO", "WARNING", "CRITICAL",
        )
    }

    test("DeliveryChannel has three options") {
        DeliveryChannel.entries.map { it.name } shouldContainExactly listOf(
            "IN_APP", "EMAIL", "WEBHOOK",
        )
    }

    test("AlertRule has required fields") {
        val rule = AlertRule(
            id = "rule-1",
            name = "VaR Limit",
            type = AlertType.VAR_BREACH,
            threshold = 100_000.0,
            operator = ComparisonOperator.GREATER_THAN,
            severity = Severity.CRITICAL,
            channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            enabled = true,
        )
        rule.id shouldBe "rule-1"
        rule.name shouldBe "VaR Limit"
        rule.type shouldBe AlertType.VAR_BREACH
        rule.threshold shouldBe 100_000.0
        rule.operator shouldBe ComparisonOperator.GREATER_THAN
        rule.severity shouldBe Severity.CRITICAL
        rule.channels shouldContainExactly listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL)
        rule.enabled shouldBe true
    }

    test("AlertEvent has required fields") {
        val now = Instant.now()
        val event = AlertEvent(
            id = "evt-1",
            ruleId = "rule-1",
            ruleName = "VaR Limit",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "VaR exceeded threshold",
            currentValue = 150_000.0,
            threshold = 100_000.0,
            portfolioId = "port-1",
            triggeredAt = now,
        )
        event.id shouldBe "evt-1"
        event.ruleId shouldBe "rule-1"
        event.ruleName shouldBe "VaR Limit"
        event.type shouldBe AlertType.VAR_BREACH
        event.severity shouldBe Severity.CRITICAL
        event.message.shouldNotBeBlank()
        event.currentValue shouldBe 150_000.0
        event.threshold shouldBe 100_000.0
        event.portfolioId shouldBe "port-1"
        event.triggeredAt shouldBe now
    }
})
