package com.kinetix.notification

import com.kinetix.notification.delivery.DeliveryRouter
import com.kinetix.notification.delivery.EmailDeliveryService
import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.delivery.WebhookDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class NotificationAlertingAcceptanceTest : BehaviorSpec({

    given("a portfolio with VaR breach alert rule configured") {
        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository())
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val email = EmailDeliveryService()
        val webhook = WebhookDeliveryService()
        val router = DeliveryRouter(listOf(inApp, email, webhook))

        rulesEngine.addRule(
            AlertRule(
                id = "rule-var",
                name = "VaR Critical Limit",
                type = AlertType.VAR_BREACH,
                threshold = 100_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
        )

        `when`("VaR calculation result exceeds the threshold") {
            val event = RiskResultEvent(
                portfolioId = "port-alert-1",
                varValue = 150_000.0,
                expectedShortfall = 180_000.0,
                calculationType = "PARAMETRIC",
                calculatedAt = "2025-01-15T10:00:00Z",
            )
            val alerts = rulesEngine.evaluate(event)
            for (alert in alerts) {
                val rule = rulesEngine.listRules().find { it.id == alert.ruleId }!!
                router.route(alert, rule.channels)
            }

            then("alert event is generated with CRITICAL severity") {
                alerts shouldHaveSize 1
                alerts[0].severity shouldBe Severity.CRITICAL
            }

            then("alert message contains portfolio ID and VaR value") {
                alerts[0].message shouldContain "port-alert-1"
                alerts[0].message shouldContain "150000"
            }

            then("alert is delivered to in-app channel") {
                inApp.getRecentAlerts() shouldHaveSize 1
                inApp.getRecentAlerts()[0].portfolioId shouldBe "port-alert-1"
            }

            then("alert is delivered to email channel") {
                email.sentEmails shouldHaveSize 1
                email.sentEmails[0].portfolioId shouldBe "port-alert-1"
            }
        }

        `when`("VaR calculation result is below the threshold") {
            val freshEngine = RulesEngine(InMemoryAlertRuleRepository())
            freshEngine.addRule(
                AlertRule(
                    id = "rule-var-2",
                    name = "VaR Critical Limit",
                    type = AlertType.VAR_BREACH,
                    threshold = 100_000.0,
                    operator = ComparisonOperator.GREATER_THAN,
                    severity = Severity.CRITICAL,
                    channels = listOf(DeliveryChannel.IN_APP),
                ),
            )
            val event = RiskResultEvent(
                portfolioId = "port-alert-1",
                varValue = 50_000.0,
                expectedShortfall = 60_000.0,
                calculationType = "PARAMETRIC",
                calculatedAt = "2025-01-15T10:05:00Z",
            )
            val alerts = freshEngine.evaluate(event)

            then("no alert is generated") {
                alerts.shouldBeEmpty()
            }
        }
    }

    given("multiple alert rules configured") {
        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository())
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val router = DeliveryRouter(listOf(inApp))

        rulesEngine.addRule(
            AlertRule(
                id = "rule-1",
                name = "VaR Limit",
                type = AlertType.VAR_BREACH,
                threshold = 100_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        rulesEngine.addRule(
            AlertRule(
                id = "rule-2",
                name = "ES Warning",
                type = AlertType.PNL_THRESHOLD,
                threshold = 500_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
        rulesEngine.addRule(
            AlertRule(
                id = "rule-3",
                name = "Disabled Rule",
                type = AlertType.RISK_LIMIT,
                threshold = 10_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.INFO,
                channels = listOf(DeliveryChannel.IN_APP),
                enabled = false,
            ),
        )

        `when`("risk result triggers some but not all rules") {
            val event = RiskResultEvent(
                portfolioId = "port-multi",
                varValue = 150_000.0,
                expectedShortfall = 200_000.0,
                calculationType = "PARAMETRIC",
                calculatedAt = "2025-01-15T11:00:00Z",
            )
            val alerts = rulesEngine.evaluate(event)
            for (alert in alerts) {
                val rule = rulesEngine.listRules().find { it.id == alert.ruleId }!!
                router.route(alert, rule.channels)
            }

            then("only matching rules produce alerts") {
                alerts shouldHaveSize 1
                alerts[0].ruleId shouldBe "rule-1"
                alerts[0].type shouldBe AlertType.VAR_BREACH
            }

            then("disabled rules do not produce alerts") {
                val disabledAlerts = alerts.filter { it.ruleId == "rule-3" }
                disabledAlerts.shouldBeEmpty()
            }
        }
    }
})
