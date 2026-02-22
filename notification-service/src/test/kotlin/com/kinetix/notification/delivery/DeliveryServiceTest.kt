package com.kinetix.notification.delivery

import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

private fun sampleAlert(id: String = "evt-1") = AlertEvent(
    id = id,
    ruleId = "rule-1",
    ruleName = "VaR Limit",
    type = AlertType.VAR_BREACH,
    severity = Severity.CRITICAL,
    message = "VaR exceeded threshold",
    currentValue = 150_000.0,
    threshold = 100_000.0,
    portfolioId = "port-1",
    triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class InAppDeliveryServiceTest : FunSpec({

    test("deliver stores alert") {
        val service = InAppDeliveryService(InMemoryAlertEventRepository())
        service.deliver(sampleAlert())
        service.getRecentAlerts() shouldHaveSize 1
        service.getRecentAlerts()[0].id shouldBe "evt-1"
    }

    test("recent alerts returns most recent first") {
        val service = InAppDeliveryService(InMemoryAlertEventRepository())
        service.deliver(sampleAlert("evt-1"))
        service.deliver(sampleAlert("evt-2"))
        service.deliver(sampleAlert("evt-3"))
        val alerts = service.getRecentAlerts()
        alerts[0].id shouldBe "evt-3"
        alerts[1].id shouldBe "evt-2"
        alerts[2].id shouldBe "evt-1"
    }

    test("recent alerts respects limit") {
        val service = InAppDeliveryService(InMemoryAlertEventRepository())
        repeat(10) { service.deliver(sampleAlert("evt-$it")) }
        val alerts = service.getRecentAlerts(limit = 3)
        alerts shouldHaveSize 3
    }
})

class EmailDeliveryServiceTest : FunSpec({

    test("deliver records email") {
        val service = EmailDeliveryService()
        service.deliver(sampleAlert())
        service.sentEmails shouldHaveSize 1
        service.sentEmails[0].id shouldBe "evt-1"
    }
})

class WebhookDeliveryServiceTest : FunSpec({

    test("deliver records webhook") {
        val service = WebhookDeliveryService()
        service.deliver(sampleAlert())
        service.sentWebhooks shouldHaveSize 1
        service.sentWebhooks[0].id shouldBe "evt-1"
    }
})

class DeliveryRouterTest : FunSpec({

    test("routes to correct channels") {
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val email = EmailDeliveryService()
        val webhook = WebhookDeliveryService()
        val router = DeliveryRouter(listOf(inApp, email, webhook))

        router.route(sampleAlert(), listOf(DeliveryChannel.IN_APP))

        inApp.getRecentAlerts() shouldHaveSize 1
        email.sentEmails.shouldBeEmpty()
        webhook.sentWebhooks.shouldBeEmpty()
    }

    test("routes to multiple channels") {
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val email = EmailDeliveryService()
        val webhook = WebhookDeliveryService()
        val router = DeliveryRouter(listOf(inApp, email, webhook))

        router.route(sampleAlert(), listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL))

        inApp.getRecentAlerts() shouldHaveSize 1
        email.sentEmails shouldHaveSize 1
        webhook.sentWebhooks.shouldBeEmpty()
    }

    test("skips channels not configured") {
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val router = DeliveryRouter(listOf(inApp))

        router.route(sampleAlert(), listOf(DeliveryChannel.IN_APP, DeliveryChannel.WEBHOOK))

        inApp.getRecentAlerts() shouldHaveSize 1
    }
})
