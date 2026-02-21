package com.kinetix.marketdata.seed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import com.kinetix.marketdata.persistence.MarketDataRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency

class DevDataSeeder(
    private val repository: MarketDataRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findLatest(InstrumentId("AAPL"))
        if (existing != null) {
            log.info("Market data already present, skipping seed")
            return
        }

        log.info("Seeding market data for {} instruments", INSTRUMENTS.size)

        for ((instrumentId, config) in INSTRUMENTS) {
            seedInstrument(instrumentId, config)
        }

        log.info("Market data seeding complete")
    }

    private suspend fun seedInstrument(instrumentId: InstrumentId, config: InstrumentConfig) {
        val currency = Currency.getInstance(config.currency)
        val latestTime = LATEST_TIME
        val startPrice = config.startPrice
        val latestPrice = config.latestPrice
        val hours = 24

        // Generate 24 hourly data points interpolating from startPrice to latestPrice
        for (i in 0..hours) {
            val fraction = i.toDouble() / hours
            // Linear interpolation with small variation
            val interpolated = startPrice + (latestPrice - startPrice) * fraction
            // Add deterministic variation based on instrument + hour
            val variation = 1.0 + (instrumentId.value.hashCode() + i).rem(100) * 0.0001
            val price = BigDecimal(interpolated * variation)
                .setScale(config.scale, RoundingMode.HALF_UP)

            val timestamp = latestTime.minus((hours - i).toLong(), ChronoUnit.HOURS)
            val point = MarketDataPoint(
                instrumentId = instrumentId,
                price = Money(price, currency),
                timestamp = timestamp,
                source = MarketDataSource.EXCHANGE,
            )
            repository.save(point)
        }
    }

    private data class InstrumentConfig(
        val currency: String,
        val startPrice: Double,
        val latestPrice: Double,
        val scale: Int = 2,
    )

    companion object {
        val LATEST_TIME: Instant = Instant.parse("2026-02-22T10:00:00Z")

        val INSTRUMENT_IDS: Set<String> = setOf(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "EURUSD", "US2Y", "US10Y", "US30Y", "GC", "SPX-PUT-4500",
        )

        private val INSTRUMENTS: Map<InstrumentId, InstrumentConfig> = mapOf(
            // Equities
            InstrumentId("AAPL") to InstrumentConfig("USD", 187.10, 189.25),
            InstrumentId("GOOGL") to InstrumentConfig("USD", 176.50, 178.90),
            InstrumentId("MSFT") to InstrumentConfig("USD", 422.30, 425.60),
            InstrumentId("AMZN") to InstrumentConfig("USD", 207.80, 210.30),
            InstrumentId("TSLA") to InstrumentConfig("USD", 244.50, 242.15),
            // FX
            InstrumentId("EURUSD") to InstrumentConfig("USD", 1.0830, 1.0856, scale = 4),
            // Fixed Income
            InstrumentId("US2Y") to InstrumentConfig("USD", 99.30, 99.40),
            InstrumentId("US10Y") to InstrumentConfig("USD", 96.85, 97.10),
            InstrumentId("US30Y") to InstrumentConfig("USD", 92.80, 93.25),
            // Commodity
            InstrumentId("GC") to InstrumentConfig("USD", 2038.20, 2058.40),
            // Derivative
            InstrumentId("SPX-PUT-4500") to InstrumentConfig("USD", 30.10, 28.75),
        )
    }
}
