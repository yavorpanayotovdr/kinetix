package com.kinetix.position.persistence

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val PORTFOLIO = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")
private val MSFT = InstrumentId("MSFT")

private fun usd(amount: String) = Money(BigDecimal(amount), USD)

private fun position(
    portfolioId: PortfolioId = PORTFOLIO,
    instrumentId: InstrumentId = AAPL,
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    averageCost: String = "150.00",
    marketPrice: String = "155.00",
) = Position(
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = usd(averageCost),
    marketPrice = usd(marketPrice),
)

class PositionRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: PositionRepository = ExposedPositionRepository()

    beforeEach {
        newSuspendedTransaction { PositionsTable.deleteAll() }
    }

    test("save and retrieve position by composite key") {
        val pos = position()
        repository.save(pos)

        val found = repository.findByKey(PORTFOLIO, AAPL)
        found.shouldNotBeNull()
        found.portfolioId shouldBe PORTFOLIO
        found.instrumentId shouldBe AAPL
        found.assetClass shouldBe AssetClass.EQUITY
        found.quantity.compareTo(BigDecimal("100")) shouldBe 0
        found.averageCost.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        found.marketPrice.amount.compareTo(BigDecimal("155.00")) shouldBe 0
        found.currency shouldBe USD
    }

    test("findByKey returns null for non-existent position") {
        repository.findByKey(PORTFOLIO, InstrumentId("GOOG")).shouldBeNull()
    }

    test("save updates existing position (upsert)") {
        repository.save(position(quantity = "100", averageCost = "150.00", marketPrice = "155.00"))

        val updated = position(quantity = "200", averageCost = "152.50", marketPrice = "160.00")
        repository.save(updated)

        val found = repository.findByKey(PORTFOLIO, AAPL)
        found.shouldNotBeNull()
        found.quantity.compareTo(BigDecimal("200")) shouldBe 0
        found.averageCost.amount.compareTo(BigDecimal("152.50")) shouldBe 0
        found.marketPrice.amount.compareTo(BigDecimal("160.00")) shouldBe 0
    }

    test("findByPortfolioId returns all positions for portfolio") {
        repository.save(position(instrumentId = AAPL))
        repository.save(position(instrumentId = MSFT, averageCost = "300.00", marketPrice = "310.00"))
        repository.save(position(portfolioId = PortfolioId("port-2"), instrumentId = AAPL))

        val results = repository.findByPortfolioId(PORTFOLIO)
        results shouldHaveSize 2
    }

    test("findByPortfolioId returns empty list for unknown portfolio") {
        repository.findByPortfolioId(PortfolioId("unknown")) shouldHaveSize 0
    }

    test("delete removes position by composite key") {
        repository.save(position())
        repository.findByKey(PORTFOLIO, AAPL).shouldNotBeNull()

        repository.delete(PORTFOLIO, AAPL)
        repository.findByKey(PORTFOLIO, AAPL).shouldBeNull()
    }

    test("delete non-existent position does not throw") {
        repository.delete(PORTFOLIO, InstrumentId("GOOG"))
    }

    test("save position with negative quantity (short position)") {
        repository.save(position(quantity = "-50"))
        val found = repository.findByKey(PORTFOLIO, AAPL)
        found.shouldNotBeNull()
        found.quantity.compareTo(BigDecimal("-50")) shouldBe 0
    }

    test("save position with zero quantity (flat position)") {
        repository.save(position(quantity = "0"))
        val found = repository.findByKey(PORTFOLIO, AAPL)
        found.shouldNotBeNull()
        found.quantity.compareTo(BigDecimal.ZERO) shouldBe 0
    }
})
