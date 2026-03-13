package com.kinetix.risk.service

import com.kinetix.risk.model.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
private val VALUATION_DATE = LocalDate.of(2026, 3, 13)

private fun completedJob(
    jobId: UUID = JOB_ID,
    portfolioId: String = "port-1",
    triggeredBy: String? = "user-a",
    runLabel: RunLabel? = null,
    promotedAt: Instant? = null,
    promotedBy: String? = null,
) = ValuationJob(
    jobId = jobId,
    portfolioId = portfolioId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = Instant.parse("2026-03-13T17:00:00Z"),
    valuationDate = VALUATION_DATE,
    completedAt = Instant.parse("2026-03-13T17:00:30Z"),
    durationMs = 30_000,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    triggeredBy = triggeredBy,
    runLabel = runLabel,
    promotedAt = promotedAt,
    promotedBy = promotedBy,
)

private fun runningJob(jobId: UUID = JOB_ID) = ValuationJob(
    jobId = jobId,
    portfolioId = "port-1",
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.RUNNING,
    startedAt = Instant.parse("2026-03-13T17:00:00Z"),
    valuationDate = VALUATION_DATE,
)

private fun failedJob(jobId: UUID = JOB_ID) = ValuationJob(
    jobId = jobId,
    portfolioId = "port-1",
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.FAILED,
    startedAt = Instant.parse("2026-03-13T17:00:00Z"),
    valuationDate = VALUATION_DATE,
    error = "Calculation failed",
)

class EodPromotionServiceTest : FunSpec({

    val jobRecorder = mockk<ValuationJobRecorder>()
    val eventPublisher = mockk<OfficialEodEventPublisher>()
    val service = EodPromotionService(jobRecorder, eventPublisher)

    beforeEach {
        clearMocks(jobRecorder, eventPublisher)
    }

    test("promotes a completed job to Official EOD") {
        val job = completedJob()
        val promoted = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-13T18:00:00Z"),
            promotedBy = "user-b",
        )
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns job
        coEvery { jobRecorder.promoteToOfficialEod(JOB_ID, "user-b", any()) } returns promoted
        coEvery { eventPublisher.publish(any()) } just Runs

        val result = service.promoteToOfficialEod(JOB_ID, "user-b")

        result.runLabel shouldBe RunLabel.OFFICIAL_EOD
        result.promotedBy shouldBe "user-b"
        coVerify { jobRecorder.promoteToOfficialEod(JOB_ID, "user-b", any()) }
        coVerify { eventPublisher.publish(match { it.jobId == JOB_ID.toString() && it.promotedBy == "user-b" }) }
    }

    test("rejects promotion when job not found") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns null

        val ex = shouldThrow<EodPromotionException.JobNotFound> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }
        ex.message shouldBe "Job not found: $JOB_ID"
    }

    test("rejects promotion when job is RUNNING") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns runningJob()

        shouldThrow<EodPromotionException.JobNotCompleted> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }
    }

    test("rejects promotion when job is FAILED") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns failedJob()

        shouldThrow<EodPromotionException.JobNotCompleted> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }
    }

    test("rejects promotion when job is already promoted") {
        val alreadyPromoted = completedJob(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-13T17:30:00Z"),
            promotedBy = "user-c",
        )
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns alreadyPromoted

        shouldThrow<EodPromotionException.AlreadyPromoted> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }
    }

    test("rejects self-promotion when triggeredBy matches promotedBy") {
        // The job was triggered by "user-a"; same user tries to promote
        val job = completedJob(triggeredBy = "user-a")
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns job

        shouldThrow<EodPromotionException.SelfPromotion> {
            service.promoteToOfficialEod(JOB_ID, "user-a")
        }
    }

    test("handles conflicting Official EOD for same portfolio and date") {
        val job = completedJob()
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns job
        coEvery { jobRecorder.promoteToOfficialEod(JOB_ID, "user-b", any()) } throws
            EodPromotionException.ConflictingOfficialEod("port-1", VALUATION_DATE.toString())

        shouldThrow<EodPromotionException.ConflictingOfficialEod> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }
    }

    test("demotes an Official EOD job back to ADHOC") {
        val promoted = completedJob(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-13T17:30:00Z"),
            promotedBy = "user-b",
        )
        val demoted = promoted.copy(runLabel = null, promotedAt = null, promotedBy = null)
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns promoted
        coEvery { jobRecorder.demoteOfficialEod(JOB_ID) } returns demoted

        val result = service.demoteFromOfficialEod(JOB_ID, "user-c")

        result.runLabel shouldBe null
        result.promotedAt shouldBe null
        coVerify { jobRecorder.demoteOfficialEod(JOB_ID) }
    }

    test("rejects demotion when job not found") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns null

        shouldThrow<EodPromotionException.JobNotFound> {
            service.demoteFromOfficialEod(JOB_ID, "user-c")
        }
    }

    test("rejects demotion when job is not promoted") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns completedJob()

        shouldThrow<EodPromotionException.NotPromoted> {
            service.demoteFromOfficialEod(JOB_ID, "user-c")
        }.message shouldBe "Job $JOB_ID is not currently promoted"
    }

    test("findOfficialEod delegates to recorder") {
        val promoted = completedJob(runLabel = RunLabel.OFFICIAL_EOD)
        coEvery { jobRecorder.findOfficialEodByDate("port-1", VALUATION_DATE) } returns promoted

        val result = service.findOfficialEod("port-1", VALUATION_DATE)

        result shouldBe promoted
    }

    test("findOfficialEod returns null when no designation exists") {
        coEvery { jobRecorder.findOfficialEodByDate("port-1", VALUATION_DATE) } returns null

        val result = service.findOfficialEod("port-1", VALUATION_DATE)

        result shouldBe null
    }

    test("publishes OfficialEodPromotedEvent after successful promotion") {
        val job = completedJob()
        val promoted = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-13T18:00:00Z"),
            promotedBy = "user-b",
        )
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns job
        coEvery { jobRecorder.promoteToOfficialEod(JOB_ID, "user-b", any()) } returns promoted
        coEvery { eventPublisher.publish(any()) } just Runs

        service.promoteToOfficialEod(JOB_ID, "user-b")

        coVerify {
            eventPublisher.publish(match { event ->
                event.jobId == JOB_ID.toString() &&
                    event.portfolioId == "port-1" &&
                    event.valuationDate == VALUATION_DATE.toString() &&
                    event.promotedBy == "user-b" &&
                    event.varValue == 5000.0 &&
                    event.expectedShortfall == 6250.0
            })
        }
    }

    test("does not publish event when promotion fails") {
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns null

        shouldThrow<EodPromotionException.JobNotFound> {
            service.promoteToOfficialEod(JOB_ID, "user-b")
        }

        coVerify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
