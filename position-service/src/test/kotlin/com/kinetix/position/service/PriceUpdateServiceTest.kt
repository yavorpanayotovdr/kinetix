package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val EUR = Currency.getInstance("EUR")
private val AAPL = InstrumentId("AAPL")

private fun usd(amount: String) = Money(BigDecimal(amount), USD)
private fun eur(amount: String) = Money(BigDecimal(amount), EUR)

private fun position(
    portfolioId: String = "port-1",
    instrumentId: InstrumentId = AAPL,
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    averageCost: Money = usd("150.00"),
    marketPrice: Money = usd("155.00"),
) = Position(
    portfolioId = PortfolioId(portfolioId),
    instrumentId = instrumentId,
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = averageCost,
    marketPrice = marketPrice,
)

class PriceUpdateServiceTest : FunSpec({

    val positionRepo = mockk<PositionRepository>()
    val service = PriceUpdateService(positionRepo)

    beforeEach {
        clearMocks(positionRepo)
    }

    test("updates market price for all positions holding the instrument") {
        val pos1 = position(portfolioId = "port-1")
        val pos2 = position(portfolioId = "port-2")
        coEvery { positionRepo.findByInstrumentId(AAPL) } returns listOf(pos1, pos2)
        coEvery { positionRepo.save(any()) } just runs

        val count = service.handle(AAPL, usd("160.00"))

        count shouldBe 2
        coVerify(exactly = 2) { positionRepo.save(match { it.marketPrice == usd("160.00") }) }
    }

    test("returns zero when no positions exist for instrument") {
        coEvery { positionRepo.findByInstrumentId(AAPL) } returns emptyList()

        val count = service.handle(AAPL, usd("160.00"))

        count shouldBe 0
        coVerify(exactly = 0) { positionRepo.save(any()) }
    }

    test("skips positions with currency mismatch") {
        val usdPosition = position(portfolioId = "port-1", averageCost = usd("150.00"), marketPrice = usd("155.00"))
        val eurPosition = position(portfolioId = "port-2", averageCost = eur("130.00"), marketPrice = eur("135.00"))
        coEvery { positionRepo.findByInstrumentId(AAPL) } returns listOf(usdPosition, eurPosition)
        coEvery { positionRepo.save(any()) } just runs

        val count = service.handle(AAPL, usd("160.00"))

        count shouldBe 1
        coVerify(exactly = 1) { positionRepo.save(match { it.portfolioId == PortfolioId("port-1") }) }
    }

    test("saves each updated position with new market price") {
        val pos = position(portfolioId = "port-1", marketPrice = usd("155.00"))
        coEvery { positionRepo.findByInstrumentId(AAPL) } returns listOf(pos)
        val savedPosition = slot<Position>()
        coEvery { positionRepo.save(capture(savedPosition)) } just runs

        service.handle(AAPL, usd("170.00"))

        savedPosition.captured.marketPrice shouldBe usd("170.00")
        savedPosition.captured.portfolioId shouldBe PortfolioId("port-1")
        savedPosition.captured.quantity.compareTo(BigDecimal("100")) shouldBe 0
        savedPosition.captured.averageCost shouldBe usd("150.00")
    }
})
