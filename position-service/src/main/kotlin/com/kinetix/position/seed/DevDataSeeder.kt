package com.kinetix.position.seed

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.TradeBookingService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
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
        private fun day(n: Long): Instant = BASE_TIME.plus(n, ChronoUnit.DAYS)

        val TRADES: List<BookTradeCommand> = listOf(
            // ── equity-growth portfolio: 5 equity trades (existing) ──
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

            // ── multi-asset portfolio: 6 trades across asset classes (existing) ──
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

            // ── fixed-income portfolio: 3 fixed income trades (existing) ──
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

            // ── emerging-markets portfolio: 5 positions (EM equities + FX) ──
            BookTradeCommand(
                tradeId = TradeId("seed-em-baba-001"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("BABA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("400"),
                price = usd("83.20"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-em-tsla-001"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("TSLA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("60"),
                price = usd("250.10"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-em-eurusd-001"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("EURUSD"),
                assetClass = AssetClass.FX,
                side = Side.BUY,
                quantity = BigDecimal("75000"),
                price = usd("1.0850"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-em-gbpusd-001"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("GBPUSD"),
                assetClass = AssetClass.FX,
                side = Side.BUY,
                quantity = BigDecimal("50000"),
                price = usd("1.2580"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-em-usdjpy-001"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("USDJPY"),
                assetClass = AssetClass.FX,
                side = Side.BUY,
                quantity = BigDecimal("80000"),
                price = usd("150.20"),
                tradedAt = day(2),
            ),
            // Partial sell after price rise
            BookTradeCommand(
                tradeId = TradeId("seed-em-baba-002"),
                portfolioId = PortfolioId("emerging-markets"),
                instrumentId = InstrumentId("BABA"),
                assetClass = AssetClass.EQUITY,
                side = Side.SELL,
                quantity = BigDecimal("100"),
                price = usd("86.50"),
                tradedAt = day(4),
            ),

            // ── macro-hedge portfolio: 6 positions (rates, commodities, FX) ──
            BookTradeCommand(
                tradeId = TradeId("seed-mh-usdjpy-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("USDJPY"),
                assetClass = AssetClass.FX,
                side = Side.BUY,
                quantity = BigDecimal("120000"),
                price = usd("149.80"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-mh-gc-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("GC"),
                assetClass = AssetClass.COMMODITY,
                side = Side.BUY,
                quantity = BigDecimal("15"),
                price = usd("2040.00"),
                tradedAt = BASE_TIME,
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-mh-cl-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("CL"),
                assetClass = AssetClass.COMMODITY,
                side = Side.BUY,
                quantity = BigDecimal("50"),
                price = usd("76.80"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-mh-si-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("SI"),
                assetClass = AssetClass.COMMODITY,
                side = Side.BUY,
                quantity = BigDecimal("200"),
                price = usd("23.10"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-mh-de10y-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("DE10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("300"),
                price = eur("97.80"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-mh-spx-put-001"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("SPX-PUT-4500"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.BUY,
                quantity = BigDecimal("40"),
                price = usd("31.20"),
                tradedAt = day(2),
            ),
            // Partial sell on gold after rally
            BookTradeCommand(
                tradeId = TradeId("seed-mh-gc-002"),
                portfolioId = PortfolioId("macro-hedge"),
                instrumentId = InstrumentId("GC"),
                assetClass = AssetClass.COMMODITY,
                side = Side.SELL,
                quantity = BigDecimal("5"),
                price = usd("2060.50"),
                tradedAt = day(4),
            ),

            // ── tech-momentum portfolio: 4 concentrated tech positions ──
            BookTradeCommand(
                tradeId = TradeId("seed-tm-nvda-001"),
                portfolioId = PortfolioId("tech-momentum"),
                instrumentId = InstrumentId("NVDA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("90"),
                price = usd("885.00"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-tm-meta-001"),
                portfolioId = PortfolioId("tech-momentum"),
                instrumentId = InstrumentId("META"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("110"),
                price = usd("502.30"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-tm-msft-001"),
                portfolioId = PortfolioId("tech-momentum"),
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("85"),
                price = usd("421.50"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-tm-googl-001"),
                portfolioId = PortfolioId("tech-momentum"),
                instrumentId = InstrumentId("GOOGL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = usd("176.80"),
                tradedAt = day(2),
            ),
            // Partial sell on META after earnings
            BookTradeCommand(
                tradeId = TradeId("seed-tm-meta-002"),
                portfolioId = PortfolioId("tech-momentum"),
                instrumentId = InstrumentId("META"),
                assetClass = AssetClass.EQUITY,
                side = Side.SELL,
                quantity = BigDecimal("30"),
                price = usd("510.00"),
                tradedAt = day(6),
            ),

            // ── balanced-income portfolio: 5 positions (bonds + dividend equities) ──
            BookTradeCommand(
                tradeId = TradeId("seed-bi-us10y-001"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("US10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("600"),
                price = usd("96.60"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-bi-us30y-001"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("US30Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("400"),
                price = usd("92.30"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-bi-de10y-001"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("DE10Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.BUY,
                quantity = BigDecimal("250"),
                price = eur("97.90"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-bi-jpm-001"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("JPM"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("150"),
                price = usd("208.40"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-bi-aapl-001"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("70"),
                price = usd("187.20"),
                tradedAt = day(4),
            ),
            // Sell some bonds to rebalance
            BookTradeCommand(
                tradeId = TradeId("seed-bi-us30y-002"),
                portfolioId = PortfolioId("balanced-income"),
                instrumentId = InstrumentId("US30Y"),
                assetClass = AssetClass.FIXED_INCOME,
                side = Side.SELL,
                quantity = BigDecimal("100"),
                price = usd("93.10"),
                tradedAt = day(6),
            ),

            // ── derivatives-book portfolio: 5 positions (options-heavy) ──
            BookTradeCommand(
                tradeId = TradeId("seed-db-spx-call-001"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("SPX-CALL-5000"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = usd("41.50"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-db-vix-put-001"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("VIX-PUT-15"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.BUY,
                quantity = BigDecimal("200"),
                price = usd("3.75"),
                tradedAt = day(1),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-db-spx-put-001"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("SPX-PUT-4500"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.BUY,
                quantity = BigDecimal("75"),
                price = usd("33.00"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-db-nvda-001"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("NVDA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("50"),
                price = usd("888.00"),
                tradedAt = day(2),
            ),
            BookTradeCommand(
                tradeId = TradeId("seed-db-tsla-001"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("TSLA"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("80"),
                price = usd("249.50"),
                tradedAt = day(4),
            ),
            // Sell some calls to take profit
            BookTradeCommand(
                tradeId = TradeId("seed-db-spx-call-002"),
                portfolioId = PortfolioId("derivatives-book"),
                instrumentId = InstrumentId("SPX-CALL-5000"),
                assetClass = AssetClass.DERIVATIVE,
                side = Side.SELL,
                quantity = BigDecimal("40"),
                price = usd("44.20"),
                tradedAt = day(6),
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
            // emerging-markets
            Pair(PortfolioId("emerging-markets"), InstrumentId("BABA")) to usd("86.10"),
            Pair(PortfolioId("emerging-markets"), InstrumentId("TSLA")) to usd("242.15"),
            Pair(PortfolioId("emerging-markets"), InstrumentId("EURUSD")) to usd("1.0856"),
            Pair(PortfolioId("emerging-markets"), InstrumentId("GBPUSD")) to usd("1.2620"),
            Pair(PortfolioId("emerging-markets"), InstrumentId("USDJPY")) to usd("150.80"),
            // macro-hedge
            Pair(PortfolioId("macro-hedge"), InstrumentId("USDJPY")) to usd("150.80"),
            Pair(PortfolioId("macro-hedge"), InstrumentId("GC")) to usd("2058.40"),
            Pair(PortfolioId("macro-hedge"), InstrumentId("CL")) to usd("78.30"),
            Pair(PortfolioId("macro-hedge"), InstrumentId("SI")) to usd("23.65"),
            Pair(PortfolioId("macro-hedge"), InstrumentId("DE10Y")) to eur("98.20"),
            Pair(PortfolioId("macro-hedge"), InstrumentId("SPX-PUT-4500")) to usd("28.75"),
            // tech-momentum
            Pair(PortfolioId("tech-momentum"), InstrumentId("NVDA")) to usd("892.50"),
            Pair(PortfolioId("tech-momentum"), InstrumentId("META")) to usd("508.40"),
            Pair(PortfolioId("tech-momentum"), InstrumentId("MSFT")) to usd("425.60"),
            Pair(PortfolioId("tech-momentum"), InstrumentId("GOOGL")) to usd("178.90"),
            // balanced-income
            Pair(PortfolioId("balanced-income"), InstrumentId("US10Y")) to usd("97.10"),
            Pair(PortfolioId("balanced-income"), InstrumentId("US30Y")) to usd("93.25"),
            Pair(PortfolioId("balanced-income"), InstrumentId("DE10Y")) to eur("98.20"),
            Pair(PortfolioId("balanced-income"), InstrumentId("JPM")) to usd("211.80"),
            Pair(PortfolioId("balanced-income"), InstrumentId("AAPL")) to usd("189.25"),
            // derivatives-book
            Pair(PortfolioId("derivatives-book"), InstrumentId("SPX-CALL-5000")) to usd("43.80"),
            Pair(PortfolioId("derivatives-book"), InstrumentId("VIX-PUT-15")) to usd("3.60"),
            Pair(PortfolioId("derivatives-book"), InstrumentId("SPX-PUT-4500")) to usd("28.75"),
            Pair(PortfolioId("derivatives-book"), InstrumentId("NVDA")) to usd("892.50"),
            Pair(PortfolioId("derivatives-book"), InstrumentId("TSLA")) to usd("242.15"),
        )
    }
}
