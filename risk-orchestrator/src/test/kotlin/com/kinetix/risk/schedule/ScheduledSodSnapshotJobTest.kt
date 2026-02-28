package com.kinetix.risk.schedule

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaselineStatus
import com.kinetix.risk.service.SodSnapshotService
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import java.time.Instant
import java.time.LocalTime

private val PORTFOLIO = PortfolioId("port-1")

class ScheduledSodSnapshotJobTest : FunSpec({

    val sodSnapshotService = mockk<SodSnapshotService>()

    beforeEach {
        clearMocks(sodSnapshotService)
    }

    test("creates AUTO snapshot when SOD time has passed and no baseline exists") {
        coEvery { sodSnapshotService.getBaselineStatus(PORTFOLIO, any()) } returns SodBaselineStatus(exists = false)
        coEvery { sodSnapshotService.createSnapshot(any(), any(), any(), any()) } just Runs

        val job = ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { listOf(PORTFOLIO) },
            sodTime = LocalTime.of(6, 0),
            nowProvider = { LocalTime.of(6, 30) },
        )

        job.tick()

        coVerify { sodSnapshotService.createSnapshot(PORTFOLIO, SnapshotType.AUTO, date = any()) }
    }

    test("skips snapshot when baseline already exists for today") {
        coEvery { sodSnapshotService.getBaselineStatus(PORTFOLIO, any()) } returns SodBaselineStatus(
            exists = true,
            baselineDate = "2025-01-15",
            snapshotType = SnapshotType.AUTO,
            createdAt = Instant.parse("2025-01-15T06:00:00Z"),
        )

        val job = ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { listOf(PORTFOLIO) },
            sodTime = LocalTime.of(6, 0),
            nowProvider = { LocalTime.of(6, 30) },
        )

        job.tick()

        coVerify(exactly = 0) { sodSnapshotService.createSnapshot(any(), any(), any(), any()) }
    }

    test("skips snapshot before SOD time") {
        val job = ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { listOf(PORTFOLIO) },
            sodTime = LocalTime.of(6, 0),
            nowProvider = { LocalTime.of(5, 30) },
        )

        job.tick()

        coVerify(exactly = 0) { sodSnapshotService.getBaselineStatus(any(), any()) }
        coVerify(exactly = 0) { sodSnapshotService.createSnapshot(any(), any(), any(), any()) }
    }

    test("handles failures gracefully and continues to next portfolio") {
        val portfolio2 = PortfolioId("port-2")
        coEvery { sodSnapshotService.getBaselineStatus(PORTFOLIO, any()) } throws RuntimeException("DB error")
        coEvery { sodSnapshotService.getBaselineStatus(portfolio2, any()) } returns SodBaselineStatus(exists = false)
        coEvery { sodSnapshotService.createSnapshot(any(), any(), any(), any()) } just Runs

        val job = ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { listOf(PORTFOLIO, portfolio2) },
            sodTime = LocalTime.of(6, 0),
            nowProvider = { LocalTime.of(6, 30) },
        )

        job.tick()

        coVerify { sodSnapshotService.createSnapshot(portfolio2, SnapshotType.AUTO, date = any()) }
    }

    test("processes multiple portfolios") {
        val portfolio2 = PortfolioId("port-2")
        coEvery { sodSnapshotService.getBaselineStatus(any(), any()) } returns SodBaselineStatus(exists = false)
        coEvery { sodSnapshotService.createSnapshot(any(), any(), any(), any()) } just Runs

        val job = ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { listOf(PORTFOLIO, portfolio2) },
            sodTime = LocalTime.of(6, 0),
            nowProvider = { LocalTime.of(6, 30) },
        )

        job.tick()

        coVerify { sodSnapshotService.createSnapshot(PORTFOLIO, SnapshotType.AUTO, date = any()) }
        coVerify { sodSnapshotService.createSnapshot(portfolio2, SnapshotType.AUTO, date = any()) }
    }
})
