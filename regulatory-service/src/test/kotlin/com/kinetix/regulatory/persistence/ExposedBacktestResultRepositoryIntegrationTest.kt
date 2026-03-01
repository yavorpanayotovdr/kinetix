package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.BacktestResultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun backtestRecord(
    id: String = UUID.randomUUID().toString(),
    portfolioId: String = "port-1",
    calculationType: String = "PARAMETRIC",
    confidenceLevel: Double = 0.99,
    totalDays: Int = 250,
    violationCount: Int = 3,
    violationRate: Double = 0.012,
    kupiecStatistic: Double = 0.15,
    kupiecPValue: Double = 0.70,
    kupiecPass: Boolean = true,
    christoffersenStatistic: Double = 0.08,
    christoffersenPValue: Double = 0.78,
    christoffersenPass: Boolean = true,
    trafficLightZone: String = "GREEN",
    calculatedAt: Instant = NOW,
) = BacktestResultRecord(
    id = id,
    portfolioId = portfolioId,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    totalDays = totalDays,
    violationCount = violationCount,
    violationRate = violationRate,
    kupiecStatistic = kupiecStatistic,
    kupiecPValue = kupiecPValue,
    kupiecPass = kupiecPass,
    christoffersenStatistic = christoffersenStatistic,
    christoffersenPValue = christoffersenPValue,
    christoffersenPass = christoffersenPass,
    trafficLightZone = trafficLightZone,
    calculatedAt = calculatedAt,
)

class ExposedBacktestResultRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: BacktestResultRepository = ExposedBacktestResultRepository()

    beforeEach {
        newSuspendedTransaction { BacktestResultsTable.deleteAll() }
    }

    test("should store backtest result") {
        val record = backtestRecord()
        repository.save(record)

        val found = repository.findLatestByPortfolioId("port-1")
        found.shouldNotBeNull()
        found.portfolioId shouldBe "port-1"
        found.totalDays shouldBe 250
        found.violationCount shouldBe 3
        found.violationRate shouldBe 0.012
        found.kupiecStatistic shouldBe 0.15
        found.kupiecPValue shouldBe 0.70
        found.kupiecPass shouldBe true
        found.christoffersenStatistic shouldBe 0.08
        found.christoffersenPValue shouldBe 0.78
        found.christoffersenPass shouldBe true
        found.trafficLightZone shouldBe "GREEN"
    }

    test("should retrieve backtest history for portfolio") {
        repository.save(backtestRecord(id = "r1", calculatedAt = Instant.parse("2026-01-10T10:00:00Z"), violationCount = 2))
        repository.save(backtestRecord(id = "r2", calculatedAt = Instant.parse("2026-01-15T10:00:00Z"), violationCount = 5))
        repository.save(backtestRecord(id = "r3", calculatedAt = Instant.parse("2026-01-20T10:00:00Z"), violationCount = 8))

        val history = repository.findByPortfolioId("port-1", limit = 10, offset = 0)
        history shouldHaveSize 3
        // Should be ordered by calculatedAt DESC
        history[0].violationCount shouldBe 8
        history[1].violationCount shouldBe 5
        history[2].violationCount shouldBe 2
    }

    test("findLatestByPortfolioId returns null for unknown portfolio") {
        repository.findLatestByPortfolioId("unknown").shouldBeNull()
    }

    test("findByPortfolioId returns empty list for unknown portfolio") {
        repository.findByPortfolioId("unknown", limit = 10, offset = 0) shouldHaveSize 0
    }

    test("findByPortfolioId respects limit and offset") {
        repository.save(backtestRecord(id = "r1", calculatedAt = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(backtestRecord(id = "r2", calculatedAt = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(backtestRecord(id = "r3", calculatedAt = Instant.parse("2026-01-20T10:00:00Z")))

        val page1 = repository.findByPortfolioId("port-1", limit = 2, offset = 0)
        page1 shouldHaveSize 2

        val page2 = repository.findByPortfolioId("port-1", limit = 2, offset = 2)
        page2 shouldHaveSize 1
    }
})
