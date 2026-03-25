package com.kinetix.regulatory.audit

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future

class GovernanceAuditPublisherTest : FunSpec({

    val producer = mockk<KafkaProducer<String, String>>()
    val publisher = GovernanceAuditPublisher(producer, topic = "governance.audit")

    test("publishes MODEL_STATUS_CHANGED event to governance.audit topic") {
        val recordSlot = slot<ProducerRecord<String, String>>()
        val future = mockk<Future<RecordMetadata>>(relaxed = true)
        every { producer.send(capture(recordSlot)) } returns future

        publisher.publish(
            GovernanceAuditEvent(
                eventType = AuditEventType.MODEL_STATUS_CHANGED,
                userId = "user-1",
                userRole = "RISK_MANAGER",
                modelName = "VaR-v2",
                details = "DRAFT->VALIDATED",
            )
        )

        verify(exactly = 1) { producer.send(any()) }
        val record = recordSlot.captured
        record.topic() shouldBe "governance.audit"
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<GovernanceAuditEvent>(record.value())
        decoded.eventType shouldBe AuditEventType.MODEL_STATUS_CHANGED
        decoded.modelName shouldBe "VaR-v2"
        decoded.details shouldBe "DRAFT->VALIDATED"
    }

    test("publishes SCENARIO_APPROVED event with scenarioId as message key") {
        val recordSlot = slot<ProducerRecord<String, String>>()
        val future = mockk<Future<RecordMetadata>>(relaxed = true)
        every { producer.send(capture(recordSlot)) } returns future

        publisher.publish(
            GovernanceAuditEvent(
                eventType = AuditEventType.SCENARIO_APPROVED,
                userId = "approver-1",
                userRole = "HEAD_OF_RISK",
                scenarioId = "sc-abc",
            )
        )

        val record = recordSlot.captured
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<GovernanceAuditEvent>(record.value())
        decoded.eventType shouldBe AuditEventType.SCENARIO_APPROVED
        decoded.scenarioId shouldBe "sc-abc"
    }

    test("publishes RBAC_ACCESS_DENIED event") {
        val recordSlot = slot<ProducerRecord<String, String>>()
        val future = mockk<Future<RecordMetadata>>(relaxed = true)
        every { producer.send(capture(recordSlot)) } returns future

        publisher.publish(
            GovernanceAuditEvent(
                eventType = AuditEventType.RBAC_ACCESS_DENIED,
                userId = "attacker",
                userRole = "UNKNOWN",
                details = "attempted /api/v1/admin",
            )
        )

        val record = recordSlot.captured
        record.topic() shouldBe "governance.audit"
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<GovernanceAuditEvent>(record.value())
        decoded.eventType shouldBe AuditEventType.RBAC_ACCESS_DENIED
    }
})
