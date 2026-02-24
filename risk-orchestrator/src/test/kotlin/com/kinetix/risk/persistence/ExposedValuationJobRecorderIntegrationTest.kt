package com.kinetix.risk.persistence

import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private fun completedJob(
    portfolioId: String = "port-1",
    triggerType: TriggerType = TriggerType.ON_DEMAND,
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    varValue: Double = 5000.0,
) = ValuationJob(
    jobId = UUID.randomUUID(),
    portfolioId = portfolioId,
    triggerType = triggerType,
    status = RunStatus.COMPLETED,
    startedAt = startedAt,
    completedAt = startedAt.plusMillis(150),
    durationMs = 150,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    steps = listOf(
        JobStep(
            name = JobStepName.FETCH_POSITIONS,
            status = RunStatus.COMPLETED,
            startedAt = startedAt,
            completedAt = startedAt.plusMillis(20),
            durationMs = 20,
            details = mapOf("positionCount" to 5),
        ),
        JobStep(
            name = JobStepName.DISCOVER_DEPENDENCIES,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(20),
            completedAt = startedAt.plusMillis(50),
            durationMs = 30,
            details = mapOf("dependencyCount" to 3, "dataTypes" to "SPOT_PRICE,YIELD_CURVE"),
        ),
        JobStep(
            name = JobStepName.FETCH_MARKET_DATA,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(50),
            completedAt = startedAt.plusMillis(80),
            durationMs = 30,
            details = mapOf("requested" to 3, "fetched" to 2),
        ),
        JobStep(
            name = JobStepName.CALCULATE_VAR,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(80),
            completedAt = startedAt.plusMillis(130),
            durationMs = 50,
            details = mapOf("varValue" to 5000.0, "expectedShortfall" to 6250.0),
        ),
        JobStep(
            name = JobStepName.PUBLISH_RESULT,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(130),
            completedAt = startedAt.plusMillis(150),
            durationMs = 20,
            details = mapOf("topic" to "risk.results"),
        ),
    ),
)

class ExposedValuationJobRecorderIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val recorder = ExposedValuationJobRecorder(db)

    beforeEach {
        newSuspendedTransaction(db = db) { ValuationJobsTable.deleteAll() }
    }

    test("saves and retrieves a completed valuation job") {
        val job = completedJob()
        recorder.save(job)

        val found = recorder.findByJobId(job.jobId)
        found.shouldNotBeNull()
        found.jobId shouldBe job.jobId
        found.portfolioId shouldBe "port-1"
        found.triggerType shouldBe TriggerType.ON_DEMAND
        found.status shouldBe RunStatus.COMPLETED
        found.calculationType shouldBe "PARAMETRIC"
        found.confidenceLevel shouldBe "CL_95"
        found.varValue shouldBe 5000.0
        found.expectedShortfall shouldBe 6250.0
        found.durationMs shouldBe 150
        found.error shouldBe null
        found.steps shouldHaveSize 5
        found.steps[0].name shouldBe JobStepName.FETCH_POSITIONS
        found.steps[0].details["positionCount"] shouldBe "5"
        found.steps[4].name shouldBe JobStepName.PUBLISH_RESULT
    }

    test("lists jobs ordered by started_at descending") {
        val job1 = completedJob(startedAt = Instant.parse("2025-01-15T10:00:00Z"))
        val job2 = completedJob(startedAt = Instant.parse("2025-01-15T11:00:00Z"))
        val job3 = completedJob(startedAt = Instant.parse("2025-01-15T09:00:00Z"))

        recorder.save(job1)
        recorder.save(job2)
        recorder.save(job3)

        val jobs = recorder.findByPortfolioId("port-1")
        jobs shouldHaveSize 3
        jobs[0].jobId shouldBe job2.jobId
        jobs[1].jobId shouldBe job1.jobId
        jobs[2].jobId shouldBe job3.jobId
    }

    test("returns null for unknown job ID") {
        recorder.findByJobId(UUID.randomUUID()).shouldBeNull()
    }

    test("respects limit and offset") {
        for (i in 0 until 5) {
            recorder.save(completedJob(startedAt = Instant.parse("2025-01-15T${10 + i}:00:00Z")))
        }

        val page1 = recorder.findByPortfolioId("port-1", limit = 2, offset = 0)
        page1 shouldHaveSize 2

        val page2 = recorder.findByPortfolioId("port-1", limit = 2, offset = 2)
        page2 shouldHaveSize 2

        val page3 = recorder.findByPortfolioId("port-1", limit = 2, offset = 4)
        page3 shouldHaveSize 1

        // No overlap between pages
        val allIds = (page1 + page2 + page3).map { it.jobId }.toSet()
        allIds shouldHaveSize 5
    }
})
