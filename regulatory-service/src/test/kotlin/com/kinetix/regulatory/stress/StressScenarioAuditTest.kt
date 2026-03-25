package com.kinetix.regulatory.stress

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
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Future

class StressScenarioAuditTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val resultRepository = mockk<StressTestResultRepository>()
    val producer = mockk<KafkaProducer<String, String>>()
    val publisher = GovernanceAuditPublisher(producer, topic = "governance.audit")
    val service = StressScenarioService(repository, resultRepository, auditPublisher = publisher)

    fun givenFuture(): Future<RecordMetadata> = mockk(relaxed = true)

    beforeEach {
        clearMocks(repository, resultRepository, producer)
    }

    test("publishes SCENARIO_APPROVED when scenario is approved") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.PENDING_APPROVAL)
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        service.approve(id, approvedBy = "approver-1")

        verify(exactly = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.SCENARIO_APPROVED
        decoded.scenarioId shouldBe id
        decoded.userId shouldBe "approver-1"
    }

    test("publishes SCENARIO_RETIRED when scenario is retired") {
        val id = UUID.randomUUID().toString()
        val scenario = aScenario(id = id, status = ScenarioStatus.APPROVED)
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        service.retire(id)

        verify(exactly = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.SCENARIO_RETIRED
        decoded.scenarioId shouldBe id
    }

    test("publishes STRESS_TEST_RUN when scenario is run") {
        val scenarioId = UUID.randomUUID().toString()
        val scenario = aScenario(id = scenarioId, status = ScenarioStatus.APPROVED)
        coEvery { repository.findById(scenarioId) } returns scenario
        coEvery { resultRepository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        service.runScenario(scenarioId, bookId = "BOOK-A", modelVersion = "v1.0")

        verify(exactly = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.STRESS_TEST_RUN
        decoded.scenarioId shouldBe scenarioId
        decoded.bookId shouldBe "BOOK-A"
    }
})

private fun aScenario(
    id: String = UUID.randomUUID().toString(),
    status: ScenarioStatus = ScenarioStatus.DRAFT,
    name: String = "Test Scenario",
) = StressScenario(
    id = id,
    name = name,
    description = "desc",
    shocks = """{"equity":-0.10}""",
    status = status,
    createdBy = "creator-1",
    approvedBy = null,
    approvedAt = null,
    createdAt = Instant.now(),
    scenarioType = ScenarioType.PARAMETRIC,
    parentScenarioId = null,
    correlationOverride = null,
    liquidityStressFactors = null,
    historicalPeriodId = null,
    targetLoss = null,
)
