package com.kinetix.referencedata.seed

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class DevDataSeeder(
    private val dividendYieldRepository: DividendYieldRepository,
    private val creditSpreadRepository: CreditSpreadRepository,
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
