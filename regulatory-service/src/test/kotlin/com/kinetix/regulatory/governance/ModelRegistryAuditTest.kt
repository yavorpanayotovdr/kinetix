package com.kinetix.regulatory.governance

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import com.kinetix.regulatory.audit.GovernanceAuditPublisher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Future

class ModelRegistryAuditTest : FunSpec({

    val repository = mockk<ModelVersionRepository>()
    val producer = mockk<KafkaProducer<String, String>>()
    val publisher = GovernanceAuditPublisher(producer, topic = "governance.audit")
    val registry = ModelRegistry(repository, auditPublisher = publisher)

    beforeEach {
        clearMocks(repository, producer)
    }

    fun givenFuture(): Future<RecordMetadata> = mockk(relaxed = true)

    test("publishes MODEL_STATUS_CHANGED audit event when model transitions from DRAFT to VALIDATED") {
        val id = UUID.randomUUID().toString()
        val model = aModel(id = id, status = ModelVersionStatus.DRAFT)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        registry.transitionStatus(id, ModelVersionStatus.VALIDATED, approvedBy = null)

        verify(exactly = 1) { producer.send(any()) }
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.MODEL_STATUS_CHANGED
        decoded.modelName shouldBe model.modelName
        decoded.details shouldBe "${ModelVersionStatus.DRAFT}->${ModelVersionStatus.VALIDATED}"
    }

    test("publishes MODEL_STATUS_CHANGED audit event when model transitions to APPROVED") {
        val id = UUID.randomUUID().toString()
        val model = aModel(id = id, status = ModelVersionStatus.VALIDATED)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit
        every { producer.send(any()) } returns givenFuture()

        registry.transitionStatus(id, ModelVersionStatus.APPROVED, approvedBy = "approver-1")

        verify(exactly = 1) { producer.send(any()) }
    }

    test("publishes MODEL_STATUS_CHANGED audit event when model transitions to RETIRED") {
        val id = UUID.randomUUID().toString()
        val model = aModel(id = id, status = ModelVersionStatus.APPROVED)
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        registry.transitionStatus(id, ModelVersionStatus.RETIRED, approvedBy = null)

        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.MODEL_STATUS_CHANGED
        decoded.details shouldBe "${ModelVersionStatus.APPROVED}->${ModelVersionStatus.RETIRED}"
    }
})

private fun aModel(
    id: String = UUID.randomUUID().toString(),
    modelName: String = "VaR-v2",
    version: String = "1.0.0",
    status: ModelVersionStatus = ModelVersionStatus.DRAFT,
) = ModelVersion(
    id = id,
    modelName = modelName,
    version = version,
    status = status,
    parameters = "{}",
    approvedBy = null,
    approvedAt = null,
    createdAt = Instant.now(),
)
