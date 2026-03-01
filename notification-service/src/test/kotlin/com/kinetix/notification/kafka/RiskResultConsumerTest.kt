package com.kinetix.notification.kafka

import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.delivery.*
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class RiskResultConsumerTest : FunSpec({

    test("consumes risk result and evaluates rules") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
            ),
        )

        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val router = DeliveryRouter(listOf(inApp))

        val riskEvent = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val eventJson = Json.encodeToString(RiskResultEvent.serializer(), riskEvent)

        val tp = TopicPartition("risk.results", 0)
        val record = ConsumerRecord("risk.results", 0, 0L, "key", eventJson)
        val records = ConsumerRecords(mapOf(tp to listOf(record)))
        val emptyRecords = ConsumerRecords<String, String>(emptyMap())

        val mockConsumer = mockk<KafkaConsumer<String, String>>()
        every { mockConsumer.subscribe(any<Collection<String>>()) } returns Unit
        var callCount = 0
        every { mockConsumer.poll(any<Duration>()) } answers {
            if (callCount++ == 0) records else emptyRecords
        }

        val consumer = RiskResultConsumer(mockConsumer, engine, router)
        val job = launch { consumer.start() }
        delay(200)
        job.cancelAndJoin()

        inApp.getRecentAlerts() shouldHaveSize 1
        inApp.getRecentAlerts()[0].type shouldBe AlertType.VAR_BREACH
        inApp.getRecentAlerts()[0].portfolioId shouldBe "port-1"
    }

    test("triggered alerts routed to delivery") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        engine.addRule(
            AlertRule(
                id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
                threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
        )

        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val email = EmailDeliveryService()
        val router = DeliveryRouter(listOf(inApp, email))

        val riskEvent = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val eventJson = Json.encodeToString(RiskResultEvent.serializer(), riskEvent)

        val tp = TopicPartition("risk.results", 0)
        val record = ConsumerRecord("risk.results", 0, 0L, "key", eventJson)
        val records = ConsumerRecords(mapOf(tp to listOf(record)))
        val emptyRecords = ConsumerRecords<String, String>(emptyMap())

        val mockConsumer = mockk<KafkaConsumer<String, String>>()
        every { mockConsumer.subscribe(any<Collection<String>>()) } returns Unit
        var callCount = 0
        every { mockConsumer.poll(any<Duration>()) } answers {
            if (callCount++ == 0) records else emptyRecords
        }

        val consumer = RiskResultConsumer(mockConsumer, engine, router)
        val job = launch { consumer.start() }
        delay(200)
        job.cancelAndJoin()

        inApp.getRecentAlerts() shouldHaveSize 1
        email.sentEmails shouldHaveSize 1
    }

    test("no rules produces no alerts") {
        val engine = RulesEngine(InMemoryAlertRuleRepository())
        val inApp = InAppDeliveryService(InMemoryAlertEventRepository())
        val router = DeliveryRouter(listOf(inApp))

        val riskEvent = RiskResultEvent("port-1", "150000.0", "180000.0", "PARAMETRIC", "2025-01-15T10:00:00Z")
        val eventJson = Json.encodeToString(RiskResultEvent.serializer(), riskEvent)

        val tp = TopicPartition("risk.results", 0)
        val record = ConsumerRecord("risk.results", 0, 0L, "key", eventJson)
        val records = ConsumerRecords(mapOf(tp to listOf(record)))
        val emptyRecords = ConsumerRecords<String, String>(emptyMap())

        val mockConsumer = mockk<KafkaConsumer<String, String>>()
        every { mockConsumer.subscribe(any<Collection<String>>()) } returns Unit
        var callCount = 0
        every { mockConsumer.poll(any<Duration>()) } answers {
            if (callCount++ == 0) records else emptyRecords
        }

        val consumer = RiskResultConsumer(mockConsumer, engine, router)
        val job = launch { consumer.start() }
        delay(200)
        job.cancelAndJoin()

        inApp.getRecentAlerts().shouldBeEmpty()
    }
})
