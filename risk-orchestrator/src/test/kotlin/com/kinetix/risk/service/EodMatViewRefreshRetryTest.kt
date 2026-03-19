package com.kinetix.risk.service

import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val REFRESH_JOB_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
private val REFRESH_DATE = LocalDate.of(2026, 3, 19)

private fun completedPromotableJob(jobId: UUID = REFRESH_JOB_ID) = ValuationJob(
    jobId = jobId,
    portfolioId = "port-1",
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = Instant.parse("2026-03-19T17:00:00Z"),
    valuationDate = REFRESH_DATE,
    completedAt = Instant.parse("2026-03-19T17:00:30Z"),
    durationMs = 30_000,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    triggeredBy = "user-a",
)

class EodMatViewRefreshRetryTest : FunSpec({

    test("materialized view is refreshed when refresher succeeds on first attempt") {
        val registry = SimpleMeterRegistry()
        val jobRecorder = mockk<ValuationJobRecorder>()
        val eventPublisher = mockk<OfficialEodEventPublisher>()
        val callCount = AtomicInteger(0)

        val job = completedPromotableJob()
        val promoted = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-19T18:00:00Z"),
            promotedBy = "user-b",
        )

        coEvery { jobRecorder.findByJobId(REFRESH_JOB_ID) } returns job
        coEvery { jobRecorder.findOfficialEodByDate("port-1", REFRESH_DATE) } returns null
        coEvery { jobRecorder.promoteToOfficialEod(REFRESH_JOB_ID, "user-b", any()) } returns promoted
        coEvery { eventPublisher.publish(any()) } just Runs

        val service = EodPromotionService(
            jobRecorder = jobRecorder,
            eventPublisher = eventPublisher,
            meterRegistry = registry,
            matViewRefresher = {
                callCount.incrementAndGet()
            },
            matViewRefreshRetryDelayMs = 1,
        )

        service.promoteToOfficialEod(REFRESH_JOB_ID, "user-b")

        callCount.get() shouldBe 1
    }

    test("materialized view is refreshed after transient failure on first attempt") {
        val registry = SimpleMeterRegistry()
        val jobRecorder = mockk<ValuationJobRecorder>()
        val eventPublisher = mockk<OfficialEodEventPublisher>()
        val callCount = AtomicInteger(0)

        val job = completedPromotableJob()
        val promoted = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-19T18:00:00Z"),
            promotedBy = "user-b",
        )

        coEvery { jobRecorder.findByJobId(REFRESH_JOB_ID) } returns job
        coEvery { jobRecorder.findOfficialEodByDate("port-1", REFRESH_DATE) } returns null
        coEvery { jobRecorder.promoteToOfficialEod(REFRESH_JOB_ID, "user-b", any()) } returns promoted
        coEvery { eventPublisher.publish(any()) } just Runs

        val service = EodPromotionService(
            jobRecorder = jobRecorder,
            eventPublisher = eventPublisher,
            meterRegistry = registry,
            matViewRefresher = {
                val attempt = callCount.incrementAndGet()
                if (attempt == 1) throw RuntimeException("DB lock timeout on first attempt")
                // second attempt succeeds
            },
            matViewRefreshRetryDelayMs = 1,
        )

        service.promoteToOfficialEod(REFRESH_JOB_ID, "user-b")

        callCount.get() shouldBe 2
    }

    test("promotion succeeds and failure counter increments when mat view refresh fails both attempts") {
        val registry = SimpleMeterRegistry()
        val jobRecorder = mockk<ValuationJobRecorder>()
        val eventPublisher = mockk<OfficialEodEventPublisher>()
        val callCount = AtomicInteger(0)

        val job = completedPromotableJob()
        val promoted = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.parse("2026-03-19T18:00:00Z"),
            promotedBy = "user-b",
        )

        coEvery { jobRecorder.findByJobId(REFRESH_JOB_ID) } returns job
        coEvery { jobRecorder.findOfficialEodByDate("port-1", REFRESH_DATE) } returns null
        coEvery { jobRecorder.promoteToOfficialEod(REFRESH_JOB_ID, "user-b", any()) } returns promoted
        coEvery { eventPublisher.publish(any()) } just Runs

        val service = EodPromotionService(
            jobRecorder = jobRecorder,
            eventPublisher = eventPublisher,
            meterRegistry = registry,
            matViewRefresher = {
                callCount.incrementAndGet()
                throw RuntimeException("DB unavailable")
            },
            matViewRefreshRetryDelayMs = 1,
        )

        // Promotion itself should still succeed even if mat view refresh fails
        val result = service.promoteToOfficialEod(REFRESH_JOB_ID, "user-b")
        result.runLabel shouldBe RunLabel.OFFICIAL_EOD

        // Refresher should have been called twice (2 attempts)
        callCount.get() shouldBe 2

        // Failure counter should be incremented
        val failureCounter = registry.find("eod.matview.refresh.failures").counter()
        failureCounter!!.count() shouldBe 1.0
    }
})
