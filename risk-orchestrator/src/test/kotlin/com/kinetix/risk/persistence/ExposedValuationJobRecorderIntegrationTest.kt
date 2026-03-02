package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private fun startedJob(
    portfolioId: String = "port-1",
    triggerType: TriggerType = TriggerType.ON_DEMAND,
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
) = ValuationJob(
    jobId = UUID.randomUUID(),
    portfolioId = portfolioId,
    triggerType = triggerType,
    status = RunStatus.RUNNING,
    startedAt = startedAt,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
)

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
    pvValue = 1_800_000.0,
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
            name = JobStepName.VALUATION,
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
        found.pvValue shouldBe 1_800_000.0
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

    test("filters jobs by time range") {
        val job1 = completedJob(startedAt = Instant.parse("2025-01-15T08:00:00Z"))
        val job2 = completedJob(startedAt = Instant.parse("2025-01-15T10:00:00Z"))
        val job3 = completedJob(startedAt = Instant.parse("2025-01-15T12:00:00Z"))

        recorder.save(job1)
        recorder.save(job2)
        recorder.save(job3)

        // from only
        val afterNine = recorder.findByPortfolioId(
            "port-1",
            from = Instant.parse("2025-01-15T09:00:00Z"),
        )
        afterNine shouldHaveSize 2
        afterNine[0].jobId shouldBe job3.jobId
        afterNine[1].jobId shouldBe job2.jobId

        // to only
        val beforeEleven = recorder.findByPortfolioId(
            "port-1",
            to = Instant.parse("2025-01-15T11:00:00Z"),
        )
        beforeEleven shouldHaveSize 2
        beforeEleven[0].jobId shouldBe job2.jobId
        beforeEleven[1].jobId shouldBe job1.jobId

        // from and to
        val range = recorder.findByPortfolioId(
            "port-1",
            from = Instant.parse("2025-01-15T09:00:00Z"),
            to = Instant.parse("2025-01-15T11:00:00Z"),
        )
        range shouldHaveSize 1
        range[0].jobId shouldBe job2.jobId

        // no filter returns all
        val all = recorder.findByPortfolioId("port-1")
        all shouldHaveSize 3
    }

    test("updates a RUNNING job to COMPLETED with all fields") {
        val job = startedJob()
        recorder.save(job)

        val found = recorder.findByJobId(job.jobId)
        found.shouldNotBeNull()
        found.status shouldBe RunStatus.RUNNING
        found.completedAt.shouldBeNull()
        found.varValue.shouldBeNull()

        val completedAt = job.startedAt.plusMillis(200)
        val updatedJob = job.copy(
            status = RunStatus.COMPLETED,
            completedAt = completedAt,
            durationMs = 200,
            varValue = 5000.0,
            expectedShortfall = 6250.0,
            pvValue = 1_800_000.0,
            steps = listOf(
                JobStep(
                    name = JobStepName.FETCH_POSITIONS,
                    status = RunStatus.COMPLETED,
                    startedAt = job.startedAt,
                    completedAt = job.startedAt.plusMillis(20),
                    durationMs = 20,
                    details = mapOf("positionCount" to 5),
                ),
            ),
        )
        recorder.update(updatedJob)

        val updated = recorder.findByJobId(job.jobId)
        updated.shouldNotBeNull()
        updated.status shouldBe RunStatus.COMPLETED
        updated.completedAt shouldBe completedAt
        updated.durationMs shouldBe 200
        updated.varValue shouldBe 5000.0
        updated.expectedShortfall shouldBe 6250.0
        updated.pvValue shouldBe 1_800_000.0
        updated.steps shouldHaveSize 1
        updated.steps[0].name shouldBe JobStepName.FETCH_POSITIONS
        updated.error shouldBe null
    }

    test("updates a RUNNING job to FAILED with error") {
        val job = startedJob()
        recorder.save(job)

        val completedAt = job.startedAt.plusMillis(50)
        val failedJob = job.copy(
            status = RunStatus.FAILED,
            completedAt = completedAt,
            durationMs = 50,
            error = "Engine down",
        )
        recorder.update(failedJob)

        val updated = recorder.findByJobId(job.jobId)
        updated.shouldNotBeNull()
        updated.status shouldBe RunStatus.FAILED
        updated.error shouldBe "Engine down"
        updated.durationMs shouldBe 50
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

    test("persists and retrieves position risk JSONB snapshot on update") {
        val job = startedJob()
        recorder.save(job)

        val positionRisk = listOf(
            PositionRisk(
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                marketValue = BigDecimal("17000.00"),
                delta = 0.85,
                gamma = 0.02,
                vega = null,
                varContribution = BigDecimal("3000.00"),
                esContribution = BigDecimal("3750.00"),
                percentageOfTotal = BigDecimal("60.00"),
            ),
            PositionRisk(
                instrumentId = InstrumentId("GOOGL"),
                assetClass = AssetClass.EQUITY,
                marketValue = BigDecimal("25000.00"),
                delta = 1.15,
                gamma = 0.03,
                vega = 500.0,
                varContribution = BigDecimal("2000.00"),
                esContribution = BigDecimal("2500.00"),
                percentageOfTotal = BigDecimal("40.00"),
            ),
        )
        val componentBreakdown = listOf(
            ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
        )
        val assetClassGreeks = listOf(
            GreekValues(AssetClass.EQUITY, 2.0, 0.05, 500.0),
        )
        val computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS)

        val updatedJob = job.copy(
            status = RunStatus.COMPLETED,
            completedAt = job.startedAt.plusMillis(200),
            durationMs = 200,
            varValue = 5000.0,
            expectedShortfall = 6250.0,
            positionRiskSnapshot = positionRisk,
            componentBreakdownSnapshot = componentBreakdown,
            computedOutputsSnapshot = computedOutputs,
            assetClassGreeksSnapshot = assetClassGreeks,
        )
        recorder.update(updatedJob)

        val found = recorder.findByJobId(job.jobId)
        found.shouldNotBeNull()
        found.positionRiskSnapshot shouldHaveSize 2
        found.positionRiskSnapshot[0].instrumentId shouldBe InstrumentId("AAPL")
        found.positionRiskSnapshot[0].marketValue.compareTo(BigDecimal("17000.00")) shouldBe 0
        found.positionRiskSnapshot[0].delta shouldBe 0.85
        found.positionRiskSnapshot[0].vega shouldBe null
        found.positionRiskSnapshot[1].instrumentId shouldBe InstrumentId("GOOGL")
        found.positionRiskSnapshot[1].vega shouldBe 500.0

        found.componentBreakdownSnapshot shouldHaveSize 1
        found.componentBreakdownSnapshot[0].assetClass shouldBe AssetClass.EQUITY
        found.componentBreakdownSnapshot[0].varContribution shouldBe 5000.0

        found.assetClassGreeksSnapshot shouldHaveSize 1
        found.assetClassGreeksSnapshot[0].delta shouldBe 2.0

        found.computedOutputsSnapshot shouldBe computedOutputs
    }

    test("returns empty snapshot lists for jobs saved without JSONB data") {
        val job = completedJob()
        recorder.save(job)

        val found = recorder.findByJobId(job.jobId)
        found.shouldNotBeNull()
        found.positionRiskSnapshot shouldHaveSize 0
        found.componentBreakdownSnapshot shouldHaveSize 0
        found.assetClassGreeksSnapshot shouldHaveSize 0
        found.computedOutputsSnapshot shouldBe emptySet()
    }

    test("findDistinctPortfolioIds returns all unique portfolio IDs") {
        recorder.save(completedJob(portfolioId = "port-1", startedAt = Instant.parse("2025-01-15T10:00:00Z")))
        recorder.save(completedJob(portfolioId = "port-2", startedAt = Instant.parse("2025-01-15T11:00:00Z")))
        recorder.save(completedJob(portfolioId = "port-1", startedAt = Instant.parse("2025-01-15T12:00:00Z")))

        val portfolios = recorder.findDistinctPortfolioIds()
        portfolios shouldHaveSize 2
        portfolios shouldContainExactlyInAnyOrder listOf("port-1", "port-2")
    }

    test("findLatestCompleted returns most recent COMPLETED job with position risk") {
        val olderJob = completedJob(startedAt = Instant.parse("2025-01-15T10:00:00Z")).copy(
            positionRiskSnapshot = listOf(
                PositionRisk(
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    marketValue = BigDecimal("17000.00"),
                    delta = 0.5,
                    gamma = null,
                    vega = null,
                    varContribution = BigDecimal("2000.00"),
                    esContribution = BigDecimal("2500.00"),
                    percentageOfTotal = BigDecimal("100.00"),
                ),
            ),
        )
        recorder.save(olderJob)

        val newerJob = completedJob(startedAt = Instant.parse("2025-01-15T12:00:00Z"), varValue = 7000.0).copy(
            positionRiskSnapshot = listOf(
                PositionRisk(
                    instrumentId = InstrumentId("GOOGL"),
                    assetClass = AssetClass.EQUITY,
                    marketValue = BigDecimal("25000.00"),
                    delta = 1.2,
                    gamma = null,
                    vega = null,
                    varContribution = BigDecimal("7000.00"),
                    esContribution = BigDecimal("8750.00"),
                    percentageOfTotal = BigDecimal("100.00"),
                ),
            ),
        )
        recorder.save(newerJob)

        // Also save a RUNNING job (should be skipped)
        recorder.save(startedJob(startedAt = Instant.parse("2025-01-15T13:00:00Z")))

        val latest = recorder.findLatestCompleted("port-1")
        latest.shouldNotBeNull()
        latest.jobId shouldBe newerJob.jobId
        latest.varValue shouldBe 7000.0
        latest.positionRiskSnapshot shouldHaveSize 1
        latest.positionRiskSnapshot[0].instrumentId shouldBe InstrumentId("GOOGL")
    }

    test("findLatestCompleted returns null when no completed jobs with position risk exist") {
        // Save a completed job WITHOUT position risk (pre-migration data)
        recorder.save(completedJob())
        // Save a RUNNING job
        recorder.save(startedJob(startedAt = Instant.parse("2025-01-15T11:00:00Z")))

        recorder.findLatestCompleted("port-1").shouldBeNull()
    }

    test("findLatestCompleted returns null for unknown portfolio") {
        recorder.save(completedJob())
        recorder.findLatestCompleted("unknown-portfolio").shouldBeNull()
    }
})
