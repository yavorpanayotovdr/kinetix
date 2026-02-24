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

private fun completedRun(
    portfolioId: String = "port-1",
    triggerType: TriggerType = TriggerType.ON_DEMAND,
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    varValue: Double = 5000.0,
) = CalculationRun(
    runId = UUID.randomUUID(),
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
        PipelineStep(
            name = PipelineStepName.FETCH_POSITIONS,
            status = RunStatus.COMPLETED,
            startedAt = startedAt,
            completedAt = startedAt.plusMillis(20),
            durationMs = 20,
            details = mapOf("positionCount" to 5),
        ),
        PipelineStep(
            name = PipelineStepName.DISCOVER_DEPENDENCIES,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(20),
            completedAt = startedAt.plusMillis(50),
            durationMs = 30,
            details = mapOf("dependencyCount" to 3, "dataTypes" to "SPOT_PRICE,YIELD_CURVE"),
        ),
        PipelineStep(
            name = PipelineStepName.FETCH_MARKET_DATA,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(50),
            completedAt = startedAt.plusMillis(80),
            durationMs = 30,
            details = mapOf("requested" to 3, "fetched" to 2),
        ),
        PipelineStep(
            name = PipelineStepName.CALCULATE_VAR,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(80),
            completedAt = startedAt.plusMillis(130),
            durationMs = 50,
            details = mapOf("varValue" to 5000.0, "expectedShortfall" to 6250.0),
        ),
        PipelineStep(
            name = PipelineStepName.PUBLISH_RESULT,
            status = RunStatus.COMPLETED,
            startedAt = startedAt.plusMillis(130),
            completedAt = startedAt.plusMillis(150),
            durationMs = 20,
            details = mapOf("topic" to "risk.results"),
        ),
    ),
)

class ExposedCalculationRunRecorderIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val recorder = ExposedCalculationRunRecorder(db)

    beforeEach {
        newSuspendedTransaction(db = db) { CalculationRunsTable.deleteAll() }
    }

    test("saves and retrieves a completed calculation run") {
        val run = completedRun()
        recorder.save(run)

        val found = recorder.findByRunId(run.runId)
        found.shouldNotBeNull()
        found.runId shouldBe run.runId
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
        found.steps[0].name shouldBe PipelineStepName.FETCH_POSITIONS
        found.steps[0].details["positionCount"] shouldBe "5"
        found.steps[4].name shouldBe PipelineStepName.PUBLISH_RESULT
    }

    test("lists runs ordered by started_at descending") {
        val run1 = completedRun(startedAt = Instant.parse("2025-01-15T10:00:00Z"))
        val run2 = completedRun(startedAt = Instant.parse("2025-01-15T11:00:00Z"))
        val run3 = completedRun(startedAt = Instant.parse("2025-01-15T09:00:00Z"))

        recorder.save(run1)
        recorder.save(run2)
        recorder.save(run3)

        val runs = recorder.findByPortfolioId("port-1")
        runs shouldHaveSize 3
        runs[0].runId shouldBe run2.runId
        runs[1].runId shouldBe run1.runId
        runs[2].runId shouldBe run3.runId
    }

    test("returns null for unknown run ID") {
        recorder.findByRunId(UUID.randomUUID()).shouldBeNull()
    }

    test("respects limit and offset") {
        for (i in 0 until 5) {
            recorder.save(completedRun(startedAt = Instant.parse("2025-01-15T${10 + i}:00:00Z")))
        }

        val page1 = recorder.findByPortfolioId("port-1", limit = 2, offset = 0)
        page1 shouldHaveSize 2

        val page2 = recorder.findByPortfolioId("port-1", limit = 2, offset = 2)
        page2 shouldHaveSize 2

        val page3 = recorder.findByPortfolioId("port-1", limit = 2, offset = 4)
        page3 shouldHaveSize 1

        // No overlap between pages
        val allIds = (page1 + page2 + page3).map { it.runId }.toSet()
        allIds shouldHaveSize 5
    }
})
