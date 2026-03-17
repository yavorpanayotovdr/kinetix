package com.kinetix.referencedata.seed

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.common.model.instrument.*
import com.kinetix.referencedata.model.Instrument
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.persistence.InstrumentRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class DevDataSeeder(
    private val dividendYieldRepository: DividendYieldRepository,
    private val creditSpreadRepository: CreditSpreadRepository,
    private val instrumentRepository: InstrumentRepository? = null,
    private val divisionRepository: DivisionRepository? = null,
    private val deskRepository: DeskRepository? = null,
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

    private data class InstrumentConfig(
        val type: InstrumentType,
        val displayName: String,
        val currency: String,
    )

    private data class CreditSpreadConfig(
        val spread: Double,
        val rating: String,
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
    }
}
