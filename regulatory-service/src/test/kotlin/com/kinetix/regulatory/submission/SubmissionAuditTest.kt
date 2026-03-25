package com.kinetix.regulatory.submission

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

class SubmissionAuditTest : FunSpec({

    val repository = mockk<SubmissionRepository>()
    val producer = mockk<KafkaProducer<String, String>>()
    val publisher = GovernanceAuditPublisher(producer, topic = "governance.audit")
    val service = SubmissionService(repository, auditPublisher = publisher)

    fun givenFuture(): Future<RecordMetadata> = mockk(relaxed = true)

    beforeEach {
        clearMocks(repository, producer)
    }

    test("publishes SUBMISSION_APPROVED when submission is approved") {
        val id = UUID.randomUUID().toString()
        val submission = aSubmission(id = id, status = SubmissionStatus.PENDING_REVIEW, preparerId = "preparer-1")
        coEvery { repository.findById(id) } returns submission
        coEvery { repository.save(any()) } returns Unit

        val publishedSlot = slot<org.apache.kafka.clients.producer.ProducerRecord<String, String>>()
        every { producer.send(capture(publishedSlot)) } returns givenFuture()

        service.approve(id, approverId = "approver-1")

        verify(exactly = 1) { producer.send(any()) }
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GovernanceAuditEvent>(publishedSlot.captured.value())
        decoded.eventType shouldBe AuditEventType.SUBMISSION_APPROVED
        decoded.submissionId shouldBe id
        decoded.userId shouldBe "approver-1"
    }

    test("does not publish audit event when submission approval is rejected due to same preparer and approver") {
        val id = UUID.randomUUID().toString()
        val submission = aSubmission(id = id, status = SubmissionStatus.PENDING_REVIEW, preparerId = "user-1")
        coEvery { repository.findById(id) } returns submission

        try {
            service.approve(id, approverId = "user-1")
        } catch (_: IllegalArgumentException) { }

        verify(exactly = 0) { producer.send(any()) }
    }
})

private fun aSubmission(
    id: String = UUID.randomUUID().toString(),
    status: SubmissionStatus = SubmissionStatus.DRAFT,
    preparerId: String = "preparer-1",
) = RegulatorySubmission(
    id = id,
    reportType = "FRTB_SA",
    status = status,
    preparerId = preparerId,
    approverId = null,
    deadline = Instant.now().plusSeconds(86400),
    submittedAt = null,
    acknowledgedAt = null,
    createdAt = Instant.now(),
)
