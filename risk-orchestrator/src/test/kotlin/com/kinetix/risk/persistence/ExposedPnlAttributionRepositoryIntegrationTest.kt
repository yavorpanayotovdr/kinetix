package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val PORTFOLIO = BookId("port-1")
private val TODAY = LocalDate.now()
private val YESTERDAY = LocalDate.now().minusDays(1)

private fun bd(value: String) = BigDecimal(value)

private fun attribution(
    bookId: BookId = PORTFOLIO,
    date: LocalDate = TODAY,
    totalPnl: String = "10.00",
    deltaPnl: String = "3.00",
    gammaPnl: String = "1.50",
    vegaPnl: String = "2.00",
    thetaPnl: String = "-0.50",
    rhoPnl: String = "0.30",
    unexplainedPnl: String = "3.70",
    positionAttributions: List<PositionPnlAttribution> = listOf(
        PositionPnlAttribution(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("7.00"),
            deltaPnl = bd("2.00"),
            gammaPnl = bd("1.00"),
            vegaPnl = bd("1.50"),
            thetaPnl = bd("-0.30"),
            rhoPnl = bd("0.20"),
            unexplainedPnl = bd("2.60"),
        ),
        PositionPnlAttribution(
            instrumentId = InstrumentId("MSFT"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("3.00"),
            deltaPnl = bd("1.00"),
            gammaPnl = bd("0.50"),
            vegaPnl = bd("0.50"),
            thetaPnl = bd("-0.20"),
            rhoPnl = bd("0.10"),
            unexplainedPnl = bd("1.10"),
        ),
    ),
) = PnlAttribution(
    bookId = bookId,
    date = date,
    currency = "USD",
    totalPnl = bd(totalPnl),
    deltaPnl = bd(deltaPnl),
    gammaPnl = bd(gammaPnl),
    vegaPnl = bd(vegaPnl),
    thetaPnl = bd(thetaPnl),
    rhoPnl = bd(rhoPnl),
    unexplainedPnl = bd(unexplainedPnl),
    positionAttributions = positionAttributions,
    calculatedAt = Instant.now(),
)

class ExposedPnlAttributionRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: PnlAttributionRepository = ExposedPnlAttributionRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { PnlAttributionsTable.deleteAll() }
    }

    test("saves and retrieves attribution by portfolio and date") {
        val attr = attribution()
        repository.save(attr)

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.bookId shouldBe PORTFOLIO
        found.date shouldBe TODAY
        found.totalPnl.compareTo(bd("10.00")) shouldBe 0
        found.deltaPnl.compareTo(bd("3.00")) shouldBe 0
        found.gammaPnl.compareTo(bd("1.50")) shouldBe 0
        found.vegaPnl.compareTo(bd("2.00")) shouldBe 0
        found.thetaPnl.compareTo(bd("-0.50")) shouldBe 0
        found.rhoPnl.compareTo(bd("0.30")) shouldBe 0
        found.unexplainedPnl.compareTo(bd("3.70")) shouldBe 0
    }

    test("persists position attributions as JSONB") {
        repository.save(attribution())

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.positionAttributions shouldHaveSize 2
        found.positionAttributions[0].instrumentId shouldBe InstrumentId("AAPL")
        found.positionAttributions[0].assetClass shouldBe AssetClass.EQUITY
        found.positionAttributions[0].totalPnl.compareTo(bd("7.00")) shouldBe 0
        found.positionAttributions[0].deltaPnl.compareTo(bd("2.00")) shouldBe 0
        found.positionAttributions[1].instrumentId shouldBe InstrumentId("MSFT")
        found.positionAttributions[1].totalPnl.compareTo(bd("3.00")) shouldBe 0
    }

    test("saves attribution with empty position attributions") {
        val attr = attribution(positionAttributions = emptyList())
        repository.save(attr)

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.positionAttributions shouldHaveSize 0
    }

    test("upserts on same portfolio-date key") {
        repository.save(attribution(totalPnl = "10.00"))
        repository.save(attribution(totalPnl = "15.00"))

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.totalPnl.compareTo(bd("15.00")) shouldBe 0
    }

    test("returns null for unknown portfolio and date") {
        repository.findByBookIdAndDate(BookId("unknown"), TODAY).shouldBeNull()
    }

    test("findLatestByBookId returns most recent attribution") {
        repository.save(attribution(date = YESTERDAY, totalPnl = "8.00"))
        repository.save(attribution(date = TODAY, totalPnl = "10.00"))

        val found = repository.findLatestByBookId(PORTFOLIO)
        found.shouldNotBeNull()
        found.date shouldBe TODAY
        found.totalPnl.compareTo(bd("10.00")) shouldBe 0
    }

    test("findLatestByBookId returns null for unknown portfolio") {
        repository.findLatestByBookId(BookId("unknown")).shouldBeNull()
    }

    test("findByBookId returns all attributions ordered by date descending") {
        repository.save(attribution(date = YESTERDAY, totalPnl = "8.00"))
        repository.save(attribution(date = TODAY, totalPnl = "10.00"))

        val found = repository.findByBookId(PORTFOLIO)
        found shouldHaveSize 2
        found[0].date shouldBe TODAY
        found[1].date shouldBe YESTERDAY
    }

    test("findByBookId returns empty for unknown portfolio") {
        repository.findByBookId(BookId("unknown")) shouldHaveSize 0
    }

    test("attributions for different portfolios are isolated") {
        val portfolio2 = BookId("port-2")
        repository.save(attribution(bookId = PORTFOLIO, totalPnl = "10.00"))
        repository.save(attribution(bookId = portfolio2, totalPnl = "20.00"))

        val port1 = repository.findByBookId(PORTFOLIO)
        port1 shouldHaveSize 1
        port1[0].totalPnl.compareTo(bd("10.00")) shouldBe 0

        val port2 = repository.findByBookId(portfolio2)
        port2 shouldHaveSize 1
        port2[0].totalPnl.compareTo(bd("20.00")) shouldBe 0
    }
})
