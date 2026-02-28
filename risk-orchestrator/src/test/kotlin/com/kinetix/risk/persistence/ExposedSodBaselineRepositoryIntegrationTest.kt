package com.kinetix.risk.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaseline
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val PORTFOLIO = PortfolioId("port-1")
private val TODAY = LocalDate.of(2025, 1, 15)
private val YESTERDAY = LocalDate.of(2025, 1, 14)
private val NOW = Instant.parse("2025-01-15T08:00:00Z")

private fun baseline(
    portfolioId: PortfolioId = PORTFOLIO,
    baselineDate: LocalDate = TODAY,
    snapshotType: SnapshotType = SnapshotType.MANUAL,
    createdAt: Instant = NOW,
) = SodBaseline(
    portfolioId = portfolioId,
    baselineDate = baselineDate,
    snapshotType = snapshotType,
    createdAt = createdAt,
)

class ExposedSodBaselineRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: SodBaselineRepository = ExposedSodBaselineRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { SodBaselinesTable.deleteAll() }
    }

    test("saves and retrieves a baseline by portfolio and date") {
        val bl = baseline()
        repository.save(bl)

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.portfolioId shouldBe PORTFOLIO
        found.baselineDate shouldBe TODAY
        found.snapshotType shouldBe SnapshotType.MANUAL
        found.createdAt shouldBe NOW
    }

    test("returns null for unknown portfolio and date") {
        repository.findByPortfolioIdAndDate(PortfolioId("unknown"), TODAY).shouldBeNull()
    }

    test("upserts on same portfolio-date key") {
        repository.save(baseline(snapshotType = SnapshotType.AUTO, createdAt = NOW))
        repository.save(baseline(snapshotType = SnapshotType.MANUAL, createdAt = Instant.parse("2025-01-15T09:00:00Z")))

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.snapshotType shouldBe SnapshotType.MANUAL
        found.createdAt shouldBe Instant.parse("2025-01-15T09:00:00Z")
    }

    test("deletes baseline by portfolio and date") {
        repository.save(baseline())

        repository.deleteByPortfolioIdAndDate(PORTFOLIO, TODAY)

        repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY).shouldBeNull()
    }

    test("delete is safe when no baseline exists") {
        repository.deleteByPortfolioIdAndDate(PORTFOLIO, TODAY)
        // no exception thrown
    }

    test("different dates are stored independently") {
        repository.save(baseline(baselineDate = TODAY))
        repository.save(baseline(baselineDate = YESTERDAY))

        repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY).shouldNotBeNull()
        repository.findByPortfolioIdAndDate(PORTFOLIO, YESTERDAY).shouldNotBeNull()
    }

    test("different portfolios are stored independently") {
        val other = PortfolioId("port-2")
        repository.save(baseline(portfolioId = PORTFOLIO))
        repository.save(baseline(portfolioId = other))

        repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY).shouldNotBeNull()
        repository.findByPortfolioIdAndDate(other, TODAY).shouldNotBeNull()
    }

    test("saves and retrieves baseline with job metadata") {
        val jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val bl = baseline().copy(sourceJobId = jobId, calculationType = "PARAMETRIC")
        repository.save(bl)

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.sourceJobId shouldBe jobId
        found.calculationType shouldBe "PARAMETRIC"
    }

    test("saves and retrieves baseline with null job metadata") {
        repository.save(baseline())

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.sourceJobId shouldBe null
        found.calculationType shouldBe null
    }
})
