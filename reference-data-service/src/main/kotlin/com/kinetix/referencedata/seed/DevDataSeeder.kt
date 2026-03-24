package com.kinetix.referencedata.seed

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.common.model.instrument.*
import com.kinetix.referencedata.model.Instrument
import com.kinetix.referencedata.model.InstrumentLiquidity
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.persistence.InstrumentLiquidityRepository
import com.kinetix.referencedata.persistence.InstrumentRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class DevDataSeeder(
    private val dividendYieldRepository: DividendYieldRepository,
    private val creditSpreadRepository: CreditSpreadRepository,
    private val instrumentRepository: InstrumentRepository? = null,
    private val divisionRepository: DivisionRepository? = null,
    private val deskRepository: DeskRepository? = null,
    private val liquidityRepository: InstrumentLiquidityRepository? = null,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = dividendYieldRepository.findLatest(InstrumentId("AAPL"))
        if (existing != null) {
            log.info("Reference data already present, skipping seed")
            return
        }

        log.info("Seeding reference data")

        seedDividendYields()
        seedCreditSpreads()
        seedInstruments()
        seedDivisions()
        seedDesks()
        seedLiquidityData()

        log.info("Reference data seeding complete")
    }

    private suspend fun seedDividendYields() {
        for ((instrumentId, yieldPercent) in DIVIDEND_YIELDS) {
            val dividendYield = DividendYield(
                instrumentId = InstrumentId(instrumentId),
                yield = yieldPercent,
                exDate = null,
                asOfDate = AS_OF,
                source = ReferenceDataSource.BLOOMBERG,
            )
            dividendYieldRepository.save(dividendYield)
        }
        log.info("Seeded {} dividend yields", DIVIDEND_YIELDS.size)
    }

    private suspend fun seedCreditSpreads() {
        for ((instrumentId, config) in CREDIT_SPREADS) {
            val creditSpread = CreditSpread(
                instrumentId = InstrumentId(instrumentId),
                spread = config.spread,
                rating = config.rating,
                asOfDate = AS_OF,
                source = ReferenceDataSource.RATING_AGENCY,
            )
            creditSpreadRepository.save(creditSpread)
        }
        log.info("Seeded {} credit spreads", CREDIT_SPREADS.size)
    }

    private suspend fun seedInstruments() {
        val repo = instrumentRepository ?: return
        for ((id, config) in INSTRUMENTS) {
            val instrument = Instrument(
                instrumentId = InstrumentId(id),
                instrumentType = config.type,
                displayName = config.displayName,
                currency = config.currency,
                createdAt = AS_OF,
                updatedAt = AS_OF,
            )
            repo.save(instrument)
        }
        log.info("Seeded {} instruments", INSTRUMENTS.size)
    }

    private suspend fun seedDivisions() {
        val repo = divisionRepository ?: return
        for ((id, config) in DIVISIONS) {
            repo.save(Division(id = DivisionId(id), name = config.name, description = config.description))
        }
        log.info("Seeded {} divisions", DIVISIONS.size)
    }

    private suspend fun seedDesks() {
        val repo = deskRepository ?: return
        for ((id, config) in DESKS) {
            repo.save(Desk(id = DeskId(id), name = config.name, divisionId = DivisionId(config.divisionId), deskHead = config.deskHead))
        }
        log.info("Seeded {} desks", DESKS.size)
    }

    private suspend fun seedLiquidityData() {
        val repo = liquidityRepository ?: return
        for ((instrumentId, config) in LIQUIDITY_DATA) {
            repo.upsert(
                InstrumentLiquidity(
                    instrumentId = instrumentId,
                    adv = config.adv,
                    bidAskSpreadBps = config.bidAskSpreadBps,
                    assetClass = config.assetClass,
                    advUpdatedAt = AS_OF,
                    createdAt = AS_OF,
                    updatedAt = AS_OF,
                )
            )
        }
        log.info("Seeded {} instrument liquidity records", LIQUIDITY_DATA.size)
    }

    private data class InstrumentConfig(
        val type: InstrumentType,
        val displayName: String,
        val currency: String,
    )

    private data class CreditSpreadConfig(
        val spread: Double,
        val rating: String,
    )

    private data class DivisionConfig(
        val name: String,
        val description: String? = null,
    )

    private data class DeskConfig(
        val name: String,
        val divisionId: String,
        val deskHead: String? = null,
    )

    private data class LiquidityConfig(
        val adv: Double,
        val bidAskSpreadBps: Double,
        val assetClass: String,
    )

    companion object {
        val AS_OF: Instant = Instant.parse("2026-02-22T10:00:00Z")

        val DIVIDEND_YIELDS: Map<String, Double> = mapOf(
            "AAPL" to 0.0055,
            "MSFT" to 0.0075,
            "GOOGL" to 0.0,
            "AMZN" to 0.0,
            "JPM" to 0.024,
            "NVDA" to 0.0003,
            "META" to 0.0035,
            "TSLA" to 0.0,
            "BABA" to 0.0,
        )

        private val INSTRUMENTS: Map<String, InstrumentConfig> = mapOf(
            "AAPL" to InstrumentConfig(
                CashEquity(currency = "USD", exchange = "NASDAQ", sector = "Technology", countryCode = "US"),
                "Apple Inc.", "USD",
            ),
            "AAPL-C-200-20260620" to InstrumentConfig(
                EquityOption(underlyingId = "AAPL", optionType = "CALL", strike = 200.0, expiryDate = "2026-06-20", exerciseStyle = "EUROPEAN", contractMultiplier = 100.0, dividendYield = 0.0055),
                "AAPL Call 200 Jun2026", "USD",
            ),
            "SPX-SEP26" to InstrumentConfig(
                EquityFuture(underlyingId = "SPX", expiryDate = "2026-09-18", contractSize = 50.0, currency = "USD"),
                "S&P 500 Sep2026 Future", "USD",
            ),
            "US10Y" to InstrumentConfig(
                GovernmentBond(currency = "USD", couponRate = 0.025, couponFrequency = 2, maturityDate = "2036-05-15", faceValue = 1000.0, dayCountConvention = "ACT/ACT"),
                "US 10Y Treasury", "USD",
            ),
            "JPM-BOND-2031" to InstrumentConfig(
                CorporateBond(currency = "USD", couponRate = 0.045, couponFrequency = 2, maturityDate = "2031-03-15", faceValue = 1000.0, issuer = "JPMorgan Chase", creditRating = "A+", seniority = "SENIOR_UNSECURED"),
                "JPM 4.5% 2031", "USD",
            ),
            "USD-SOFR-5Y" to InstrumentConfig(
                InterestRateSwap(notional = 10_000_000.0, currency = "USD", fixedRate = 0.035, floatIndex = "SOFR", maturityDate = "2031-03-16", effectiveDate = "2026-03-16", payReceive = "PAY_FIXED"),
                "USD SOFR 5Y IRS", "USD",
            ),
            "EURUSD" to InstrumentConfig(
                FxSpot(baseCurrency = "EUR", quoteCurrency = "USD"),
                "EUR/USD Spot", "USD",
            ),
            "GBPUSD-3M" to InstrumentConfig(
                FxForward(baseCurrency = "GBP", quoteCurrency = "USD", deliveryDate = "2026-06-16", forwardRate = 1.28),
                "GBP/USD 3M Forward", "USD",
            ),
            "EURUSD-P-1.08-SEP26" to InstrumentConfig(
                FxOption(baseCurrency = "EUR", quoteCurrency = "USD", optionType = "PUT", strike = 1.08, expiryDate = "2026-09-15"),
                "EUR/USD Put 1.08 Sep2026", "USD",
            ),
            "WTI-AUG26" to InstrumentConfig(
                CommodityFuture(commodity = "WTI", expiryDate = "2026-08-20", contractSize = 1000.0, currency = "USD"),
                "WTI Crude Aug2026", "USD",
            ),
            "GC-C-2200-DEC26" to InstrumentConfig(
                CommodityOption(underlyingId = "GC", optionType = "CALL", strike = 2200.0, expiryDate = "2026-12-28", contractMultiplier = 100.0),
                "Gold Call 2200 Dec2026", "USD",
            ),
        )

        private val CREDIT_SPREADS: Map<String, CreditSpreadConfig> = mapOf(
            "US2Y" to CreditSpreadConfig(spread = 0.0005, rating = "AAA"),
            "US10Y" to CreditSpreadConfig(spread = 0.0010, rating = "AAA"),
            "US30Y" to CreditSpreadConfig(spread = 0.0015, rating = "AAA"),
            "DE10Y" to CreditSpreadConfig(spread = 0.0008, rating = "AAA"),
            "JPM" to CreditSpreadConfig(spread = 0.0050, rating = "A+"),
            "BABA" to CreditSpreadConfig(spread = 0.0180, rating = "A"),
        )

        private val DIVISIONS: Map<String, DivisionConfig> = mapOf(
            "equities" to DivisionConfig(name = "Equities"),
            "fixed-income-rates" to DivisionConfig(name = "Fixed Income & Rates"),
            "multi-asset" to DivisionConfig(name = "Multi-Asset"),
        )

        // ADV and bid-ask spread data for all 11 instrument types.
        // ADV is approximate daily traded notional in USD.
        // Tier guidance: <10% ADV = HIGH_LIQUID (1d), 10-25% = LIQUID (3d),
        //                25-50% = SEMI_LIQUID (5d), >50% or no ADV = ILLIQUID (10d).
        private val LIQUIDITY_DATA: Map<String, LiquidityConfig> = mapOf(
            // Large-cap equities — highly liquid
            "AAPL"                  to LiquidityConfig(adv = 80_000_000.0,  bidAskSpreadBps = 1.0,   assetClass = "EQUITY"),
            // Equity option on AAPL — liquid via underlying, wider spread
            "AAPL-C-200-20260620"   to LiquidityConfig(adv = 5_000_000.0,   bidAskSpreadBps = 20.0,  assetClass = "EQUITY"),
            // Index future — highly liquid
            "SPX-SEP26"             to LiquidityConfig(adv = 120_000_000.0, bidAskSpreadBps = 0.5,   assetClass = "EQUITY"),
            // On-the-run US Treasury — highly liquid
            "US10Y"                 to LiquidityConfig(adv = 500_000_000.0, bidAskSpreadBps = 0.25,  assetClass = "FIXED_INCOME"),
            // Investment-grade corporate bond — liquid but wider spread
            "JPM-BOND-2031"         to LiquidityConfig(adv = 15_000_000.0,  bidAskSpreadBps = 10.0,  assetClass = "FIXED_INCOME"),
            // Vanilla IRS — semi-liquid OTC instrument
            "USD-SOFR-5Y"           to LiquidityConfig(adv = 8_000_000.0,   bidAskSpreadBps = 5.0,   assetClass = "FIXED_INCOME"),
            // Spot FX — most liquid market
            "EURUSD"                to LiquidityConfig(adv = 1_000_000_000.0, bidAskSpreadBps = 0.1, assetClass = "FX"),
            // FX forward — liquid but less than spot
            "GBPUSD-3M"             to LiquidityConfig(adv = 200_000_000.0, bidAskSpreadBps = 1.0,   assetClass = "FX"),
            // FX option — semi-liquid OTC
            "EURUSD-P-1.08-SEP26"   to LiquidityConfig(adv = 20_000_000.0,  bidAskSpreadBps = 15.0,  assetClass = "FX"),
            // WTI crude futures — highly liquid exchange-traded
            "WTI-AUG26"             to LiquidityConfig(adv = 350_000_000.0, bidAskSpreadBps = 2.0,   assetClass = "COMMODITY"),
            // Gold option — semi-liquid
            "GC-C-2200-DEC26"       to LiquidityConfig(adv = 10_000_000.0,  bidAskSpreadBps = 25.0,  assetClass = "COMMODITY"),
        )

        private val DESKS: Map<String, DeskConfig> = mapOf(
            "equity-growth" to DeskConfig(name = "Equity Growth", divisionId = "equities"),
            "tech-momentum" to DeskConfig(name = "Tech Momentum", divisionId = "equities"),
            "emerging-markets" to DeskConfig(name = "Emerging Markets", divisionId = "equities"),
            "rates-trading" to DeskConfig(name = "Rates Trading", divisionId = "fixed-income-rates"),
            "multi-asset-strategies" to DeskConfig(name = "Multi-Asset Strategies", divisionId = "multi-asset"),
            "macro-hedge" to DeskConfig(name = "Macro Hedge", divisionId = "multi-asset"),
            "balanced-income" to DeskConfig(name = "Balanced Income", divisionId = "multi-asset"),
            "derivatives-trading" to DeskConfig(name = "Derivatives Trading", divisionId = "multi-asset"),
        )
    }
}
