package com.kinetix.position.seed

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.TradeBookingService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class DevDataSeeder(
    private val tradeBookingService: TradeBookingService,
    private val positionRepository: PositionRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = positionRepository.findDistinctPortfolioIds()
        if (existing.isNotEmpty()) {
            log.info("Seed data already present ({} portfolios), skipping", existing.size)
            return
        }

        log.info("Seeding dev data: {} trades across {} portfolios", TRADES.size, TRADES.map { it.portfolioId }.distinct().size)

        for (trade in TRADES) {
            tradeBookingService.handle(trade)
        }

        // Update positions with realistic market prices (trades only set averageCost)
        for ((key, marketPrice) in MARKET_PRICES) {
            val position = positionRepository.findByKey(key.first, key.second) ?: continue
            positionRepository.save(position.markToMarket(marketPrice))
        }

        log.info("Dev data seeding complete")
    }

    companion object {
        private val USD = Currency.getInstance("USD")
        private val EUR = Currency.getInstance("EUR")
        private val BASE_TIME = Instant.parse("2026-02-21T14:00:00Z")

        private fun usd(amount: String) = Money(BigDecimal(amount), USD)
        private fun eur(amount: String) = Money(BigDecimal(amount), EUR)

        val TRADES: List<BookTradeCommand> = listOf(
            // equity-growth portfolio: 5 equity trades
            BookTradeCommand(
                tradeId = TradeId("seed-eq-aapl-001"),
                portfolioId = PortfolioId("equity-growth"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("150"),
                price = usd("185.50"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-eq-googl-001"),
                portfolioId = PortfolioId("equity-growth"),
                instrumentId = InstrumentId("GOOGL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("80"),
                price = usd("175.20"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-eq-msft-001"),
                portfolioId = PortfolioId("equity-growth"),
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("120"),
                price = usd("420.00"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-eq-amzn-001"),
                portfolioId = PortfolioId("equity-growth"),
                instrumentId = InstrumentId("AMZN"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = usd("205.75"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-eq-tsla-001"),
                portfolioId = PortfolioId("equity-growth"),
                instrumentId = InstrumentId("TSLA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("200"),
                price = usd("248.30"),
                tradedAt = BASE_TIME,
            ),

            // multi-asset portfolio: 6 trades across asset classes
            BookTradeCommand(
                tradeId = TradeId("seed-ma-aapl-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("50"),
                price = usd("186.00"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-ma-eurusd-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("EURUSD"),
                assetClass = AssetClass.FX,
                side = Side.BUY,
                quantity = BigDecimal("100000"),
                price = usd("1.0842"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-ma-us10y-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("US10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("500"),
                price = usd("96.75"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-ma-gc-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("GC"),
                assetClass = AssetClass.COMMODITY,
                side = Side.BUY,
                quantity = BigDecimal("10"),
                price = usd("2045.60"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-ma-spx-put-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("SPX-PUT-4500"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.BUY,
                quantity = BigDecimal("25"),
                price = usd("32.50"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-ma-msft-001"),
                portfolioId = PortfolioId("multi-asset"),
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("75"),
                price = usd("418.50"),
                tradedAt = BASE_TIME,
            ),

            // fixed-income portfolio: 3 fixed income trades
            BookTradeCommand(
                tradeId = TradeId("seed-fi-us2y-001"),
                portfolioId = PortfolioId("fixed-income"),
                instrumentId = InstrumentId("US2Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("1000"),
                price = usd("99.25"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-fi-us10y-001"),
                portfolioId = PortfolioId("fixed-income"),
                instrumentId = InstrumentId("US10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("800"),
                price = usd("96.50"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-fi-us30y-001"),
                portfolioId = PortfolioId("fixed-income"),
                instrumentId = InstrumentId("US30Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("500"),
                price = usd("92.10"),
                tradedAt = BASE_TIME,
            ),
        )

        val MARKET_PRICES: Map<Pair<PortfolioId, InstrumentId>, Money> = mapOf(
            // equity-growth
            Pair(PortfolioId("equity-growth"), InstrumentId("AAPL")) to usd("189.25"),
            Pair(PortfolioId("equity-growth"), InstrumentId("GOOGL")) to usd("178.90"),
            Pair(PortfolioId("equity-growth"), InstrumentId("MSFT")) to usd("425.60"),
            Pair(PortfolioId("equity-growth"), InstrumentId("AMZN")) to usd("210.30"),
            Pair(PortfolioId("equity-growth"), InstrumentId("TSLA")) to usd("242.15"),
            // multi-asset
            Pair(PortfolioId("multi-asset"), InstrumentId("AAPL")) to usd("189.25"),
            Pair(PortfolioId("multi-asset"), InstrumentId("EURUSD")) to usd("1.0856"),
            Pair(PortfolioId("multi-asset"), InstrumentId("US10Y")) to usd("97.10"),
            Pair(PortfolioId("multi-asset"), InstrumentId("GC")) to usd("2058.40"),
            Pair(PortfolioId("multi-asset"), InstrumentId("SPX-PUT-4500")) to usd("28.75"),
            Pair(PortfolioId("multi-asset"), InstrumentId("MSFT")) to usd("425.60"),
            // fixed-income
            Pair(PortfolioId("fixed-income"), InstrumentId("US2Y")) to usd("99.40"),
            Pair(PortfolioId("fixed-income"), InstrumentId("US10Y")) to usd("97.10"),
            Pair(PortfolioId("fixed-income"), InstrumentId("US30Y")) to usd("93.25"),
        )
    }
}
