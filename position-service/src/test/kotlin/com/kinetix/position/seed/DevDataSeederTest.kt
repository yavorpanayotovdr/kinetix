package com.kinetix.position.seed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.BookTradeResult
import com.kinetix.position.service.TradeBookingService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class DevDataSeederTest : FunSpec({

    val tradeBookingService = mockk<TradeBookingService>()
    val positionRepository = mockk<PositionRepository>()
    val seeder = DevDataSeeder(tradeBookingService, positionRepository)

    beforeEach {
        clearMocks(tradeBookingService, positionRepository)
    }

    test("seeds all trades when database is empty") {
        coEvery { positionRepository.findDistinctPortfolioIds() } returns emptyList()
        coEvery { tradeBookingService.handle(any()) } answers {
            val cmd = firstArg<BookTradeCommand>()
            BookTradeResult(
                trade = com.kinetix.common.model.Trade(
                    tradeId = cmd.tradeId,
                    portfolioId = cmd.portfolioId,
                    instrumentId = cmd.instrumentId,
                    assetClass = cmd.assetClass,
                    side = cmd.side,
                    quantity = cmd.quantity,
                    price = cmd.price,
                    tradedAt = cmd.tradedAt,
                ),
                position = Position.empty(cmd.portfolioId, cmd.instrumentId, cmd.assetClass, cmd.price.currency),
            )
        }
        coEvery { positionRepository.findByKey(any(), any()) } returns null
        coEvery { positionRepository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = DevDataSeeder.TRADES.size) { tradeBookingService.handle(any()) }
    }

    test("skips seeding when portfolios already exist") {
        coEvery { positionRepository.findDistinctPortfolioIds() } returns listOf(
            PortfolioId("equity-growth"),
        )

        seeder.seed()

        coVerify(exactly = 0) { tradeBookingService.handle(any()) }
    }

    test("updates market prices after booking trades") {
        coEvery { positionRepository.findDistinctPortfolioIds() } returns emptyList()
        coEvery { tradeBookingService.handle(any()) } answers {
            val cmd = firstArg<BookTradeCommand>()
            BookTradeResult(
                trade = com.kinetix.common.model.Trade(
                    tradeId = cmd.tradeId,
                    portfolioId = cmd.portfolioId,
                    instrumentId = cmd.instrumentId,
                    assetClass = cmd.assetClass,
                    side = cmd.side,
                    quantity = cmd.quantity,
                    price = cmd.price,
                    tradedAt = cmd.tradedAt,
                ),
                position = Position.empty(cmd.portfolioId, cmd.instrumentId, cmd.assetClass, cmd.price.currency),
            )
        }
        coEvery { positionRepository.findByKey(any(), any()) } returns null
        coEvery { positionRepository.save(any()) } just runs

        seeder.seed()

        coVerify(atLeast = DevDataSeeder.MARKET_PRICES.size) { positionRepository.findByKey(any(), any()) }
    }

    test("trade data has correct number of trades per portfolio") {
        val tradesByPortfolio = DevDataSeeder.TRADES.groupBy { it.portfolioId.value }
        tradesByPortfolio["equity-growth"]!!.size shouldBe 5
        tradesByPortfolio["multi-asset"]!!.size shouldBe 6
        tradesByPortfolio["fixed-income"]!!.size shouldBe 3
        tradesByPortfolio["emerging-markets"]!!.size shouldBe 6
        tradesByPortfolio["macro-hedge"]!!.size shouldBe 7
        tradesByPortfolio["tech-momentum"]!!.size shouldBe 5
        tradesByPortfolio["balanced-income"]!!.size shouldBe 6
        tradesByPortfolio["derivatives-book"]!!.size shouldBe 6
    }

    test("all trade IDs are unique") {
        val tradeIds = DevDataSeeder.TRADES.map { it.tradeId.value }
        tradeIds.distinct().size shouldBe tradeIds.size
    }

    test("market prices cover all positions") {
        val positionKeys = DevDataSeeder.TRADES.map { Pair(it.portfolioId, it.instrumentId) }.toSet()
        val marketPriceKeys = DevDataSeeder.MARKET_PRICES.keys
        positionKeys shouldBe marketPriceKeys
    }
})
