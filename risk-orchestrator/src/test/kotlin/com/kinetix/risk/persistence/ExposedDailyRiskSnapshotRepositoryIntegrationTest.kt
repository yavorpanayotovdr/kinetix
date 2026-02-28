package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.DailyRiskSnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.LocalDate

private val PORTFOLIO = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")
private val MSFT = InstrumentId("MSFT")
private val TODAY = LocalDate.of(2025, 1, 15)
private val YESTERDAY = LocalDate.of(2025, 1, 14)

private fun snapshot(
    portfolioId: PortfolioId = PORTFOLIO,
    snapshotDate: LocalDate = TODAY,
    instrumentId: InstrumentId = AAPL,
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    marketPrice: String = "150.00",
    delta: Double? = 0.85,
    gamma: Double? = 0.02,
    vega: Double? = 1500.0,
    theta: Double? = -50.0,
    rho: Double? = 30.0,
) = DailyRiskSnapshot(
    portfolioId = portfolioId,
    snapshotDate = snapshotDate,
    instrumentId = instrumentId,
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    marketPrice = BigDecimal(marketPrice),
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
)

class ExposedDailyRiskSnapshotRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: DailyRiskSnapshotRepository = ExposedDailyRiskSnapshotRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { DailyRiskSnapshotsTable.deleteAll() }
    }

    test("saves and retrieves a snapshot by portfolio and date") {
        val snap = snapshot()
        repository.save(snap)

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found shouldHaveSize 1
        found[0].portfolioId shouldBe PORTFOLIO
        found[0].snapshotDate shouldBe TODAY
        found[0].instrumentId shouldBe AAPL
        found[0].assetClass shouldBe AssetClass.EQUITY
        found[0].quantity.compareTo(BigDecimal("100")) shouldBe 0
        found[0].marketPrice.compareTo(BigDecimal("150.00")) shouldBe 0
        found[0].delta shouldBe 0.85
        found[0].gamma shouldBe 0.02
        found[0].vega shouldBe 1500.0
        found[0].theta shouldBe -50.0
        found[0].rho shouldBe 30.0
    }

    test("saves snapshot with null greeks") {
        val snap = snapshot(delta = null, gamma = null, vega = null, theta = null, rho = null)
        repository.save(snap)

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found shouldHaveSize 1
        found[0].delta shouldBe null
        found[0].gamma shouldBe null
        found[0].vega shouldBe null
        found[0].theta shouldBe null
        found[0].rho shouldBe null
    }

    test("upserts on same portfolio-date-instrument key") {
        repository.save(snapshot(marketPrice = "150.00", delta = 0.85))
        repository.save(snapshot(marketPrice = "155.00", delta = 0.90))

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found shouldHaveSize 1
        found[0].marketPrice.compareTo(BigDecimal("155.00")) shouldBe 0
        found[0].delta shouldBe 0.90
    }

    test("returns multiple instruments for the same portfolio and date") {
        repository.save(snapshot(instrumentId = AAPL))
        repository.save(snapshot(instrumentId = MSFT, marketPrice = "300.00"))

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found shouldHaveSize 2
        found.map { it.instrumentId }.toSet() shouldBe setOf(AAPL, MSFT)
    }

    test("returns empty list for unknown portfolio and date") {
        repository.findByPortfolioIdAndDate(PortfolioId("unknown"), TODAY) shouldHaveSize 0
    }

    test("findByPortfolioIdAndDate filters by date correctly") {
        repository.save(snapshot(snapshotDate = TODAY))
        repository.save(snapshot(snapshotDate = YESTERDAY))

        val todaySnaps = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        todaySnaps shouldHaveSize 1
        todaySnaps[0].snapshotDate shouldBe TODAY

        val yesterdaySnaps = repository.findByPortfolioIdAndDate(PORTFOLIO, YESTERDAY)
        yesterdaySnaps shouldHaveSize 1
        yesterdaySnaps[0].snapshotDate shouldBe YESTERDAY
    }

    test("saveAll persists multiple snapshots") {
        val snapshots = listOf(
            snapshot(instrumentId = AAPL),
            snapshot(instrumentId = MSFT, marketPrice = "300.00"),
        )
        repository.saveAll(snapshots)

        val found = repository.findByPortfolioIdAndDate(PORTFOLIO, TODAY)
        found shouldHaveSize 2
    }

    test("findByPortfolioId returns all snapshots across dates") {
        repository.save(snapshot(snapshotDate = TODAY, instrumentId = AAPL))
        repository.save(snapshot(snapshotDate = YESTERDAY, instrumentId = AAPL))
        repository.save(snapshot(snapshotDate = TODAY, instrumentId = MSFT))

        val found = repository.findByPortfolioId(PORTFOLIO)
        found shouldHaveSize 3
    }

    test("findByPortfolioId returns empty for unknown portfolio") {
        repository.findByPortfolioId(PortfolioId("unknown")) shouldHaveSize 0
    }
})
