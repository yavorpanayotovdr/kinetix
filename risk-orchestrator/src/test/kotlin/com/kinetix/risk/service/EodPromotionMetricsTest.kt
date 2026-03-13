package com.kinetix.risk.service

import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EodPromotionMetricsTest : FunSpec({

    val registry = SimpleMeterRegistry()
    val jobRecorder = mockk<ValuationJobRecorder>()
    val eventPublisher = mockk<OfficialEodEventPublisher>()
    val service = EodPromotionService(jobRecorder, eventPublisher, registry)

    beforeEach {
        clearMocks(jobRecorder, eventPublisher)
        registry.clear()
    }

    test("successful promotion increments eod.promotion.requests counter with result=success") {
        val jobId = UUID.randomUUID()
        val job = ValuationJob(
            jobId = jobId,
            portfolioId = "port-1",
            triggerType = TriggerType.ON_DEMAND,
            status = RunStatus.COMPLETED,
            startedAt = Instant.now(),
            valuationDate = LocalDate.of(2025, 1, 15),
            triggeredBy = "user-a",
        )
        val promotedJob = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.now(),
            promotedBy = "user-b",
        )

        coEvery { jobRecorder.findByJobId(jobId) } returns job
        coEvery { jobRecorder.findOfficialEodByDate("port-1", LocalDate.of(2025, 1, 15)) } returns null
        coEvery { jobRecorder.promoteToOfficialEod(jobId, "user-b", any()) } returns promotedJob
        coEvery { eventPublisher.publish(any()) } just Runs

        service.promoteToOfficialEod(jobId, "user-b")

        val counter = registry.find("eod.promotion.requests")
            .tag("result", "success")
            .counter()
        counter!!.count() shouldBe 1.0
    }

    test("failed promotion increments eod.promotion.requests counter with result=error") {
        val jobId = UUID.randomUUID()
        coEvery { jobRecorder.findByJobId(jobId) } returns null

        try {
            service.promoteToOfficialEod(jobId, "user-b")
        } catch (_: EodPromotionException.JobNotFound) {
            // expected
        }

        val counter = registry.find("eod.promotion.requests")
            .tag("result", "error")
            .counter()
        counter!!.count() shouldBe 1.0
    }

    test("promotion records duration in eod.promotion.duration timer") {
        val jobId = UUID.randomUUID()
        val job = ValuationJob(
            jobId = jobId,
            portfolioId = "port-1",
            triggerType = TriggerType.ON_DEMAND,
            status = RunStatus.COMPLETED,
            startedAt = Instant.now(),
            valuationDate = LocalDate.of(2025, 1, 15),
            triggeredBy = "user-a",
        )
        val promotedJob = job.copy(
            runLabel = RunLabel.OFFICIAL_EOD,
            promotedAt = Instant.now(),
            promotedBy = "user-b",
        )

        coEvery { jobRecorder.findByJobId(jobId) } returns job
        coEvery { jobRecorder.findOfficialEodByDate("port-1", LocalDate.of(2025, 1, 15)) } returns null
        coEvery { jobRecorder.promoteToOfficialEod(jobId, "user-b", any()) } returns promotedJob
        coEvery { eventPublisher.publish(any()) } just Runs

        service.promoteToOfficialEod(jobId, "user-b")

        val timer = registry.find("eod.promotion.duration").timer()
        timer!!.count() shouldBe 1
    }
})
