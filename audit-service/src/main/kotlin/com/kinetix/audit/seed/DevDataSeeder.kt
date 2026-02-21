package com.kinetix.audit.seed

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class DevDataSeeder(
    private val repository: AuditEventRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findAll()
        if (existing.isNotEmpty()) {
            log.info("Audit events already present ({} events), skipping seed", existing.size)
            return
        }

        log.info("Seeding {} audit events", EVENTS.size)

        for (event in EVENTS) {
            repository.save(event)
        }

        log.info("Audit event seeding complete")
    }

    companion object {
        private val RECEIVED_AT = Instant.parse("2026-02-21T14:00:01Z")
        private const val TRADED_AT = "2026-02-21T14:00:00Z"

        val EVENTS: List<AuditEvent> = listOf(
            // equity-growth portfolio: 5 equity trades
            AuditEvent(
                tradeId = "seed-eq-aapl-001",
                portfolioId = "equity-growth",
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "150",
                priceAmount = "185.50",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-eq-googl-001",
                portfolioId = "equity-growth",
                instrumentId = "GOOGL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "80",
                priceAmount = "175.20",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-eq-msft-001",
                portfolioId = "equity-growth",
                instrumentId = "MSFT",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "120",
                priceAmount = "420.00",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-eq-amzn-001",
                portfolioId = "equity-growth",
                instrumentId = "AMZN",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "100",
                priceAmount = "205.75",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-eq-tsla-001",
                portfolioId = "equity-growth",
                instrumentId = "TSLA",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "200",
                priceAmount = "248.30",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),

            // multi-asset portfolio: 6 trades across asset classes
            AuditEvent(
                tradeId = "seed-ma-aapl-001",
                portfolioId = "multi-asset",
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "50",
                priceAmount = "186.00",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-ma-eurusd-001",
                portfolioId = "multi-asset",
                instrumentId = "EURUSD",
                assetClass = "FX",
                side = "BUY",
                quantity = "100000",
                priceAmount = "1.0842",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-ma-us10y-001",
                portfolioId = "multi-asset",
                instrumentId = "US10Y",
                assetClass = "FIXED_INCOME",
                side = "BUY",
                quantity = "500",
                priceAmount = "96.75",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-ma-gc-001",
                portfolioId = "multi-asset",
                instrumentId = "GC",
                assetClass = "COMMODITY",
                side = "BUY",
                quantity = "10",
                priceAmount = "2045.60",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-ma-spx-put-001",
                portfolioId = "multi-asset",
                instrumentId = "SPX-PUT-4500",
                assetClass = "DERIVATIVE",
                side = "BUY",
                quantity = "25",
                priceAmount = "32.50",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-ma-msft-001",
                portfolioId = "multi-asset",
                instrumentId = "MSFT",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "75",
                priceAmount = "418.50",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),

            // fixed-income portfolio: 3 fixed income trades
            AuditEvent(
                tradeId = "seed-fi-us2y-001",
                portfolioId = "fixed-income",
                instrumentId = "US2Y",
                assetClass = "FIXED_INCOME",
                side = "BUY",
                quantity = "1000",
                priceAmount = "99.25",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-fi-us10y-001",
                portfolioId = "fixed-income",
                instrumentId = "US10Y",
                assetClass = "FIXED_INCOME",
                side = "BUY",
                quantity = "800",
                priceAmount = "96.50",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
            AuditEvent(
                tradeId = "seed-fi-us30y-001",
                portfolioId = "fixed-income",
                instrumentId = "US30Y",
                assetClass = "FIXED_INCOME",
                side = "BUY",
                quantity = "500",
                priceAmount = "92.10",
                priceCurrency = "USD",
                tradedAt = TRADED_AT,
                receivedAt = RECEIVED_AT,
            ),
        )
    }
}
