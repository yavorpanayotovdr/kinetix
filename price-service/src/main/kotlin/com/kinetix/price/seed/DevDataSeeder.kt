package com.kinetix.price.seed

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import com.kinetix.price.persistence.PriceRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency

class DevDataSeeder(
    private val repository: PriceRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findLatest(InstrumentId("AAPL"))
        if (existing != null) {
            log.info("Price data already present, skipping seed")
            return
        }

        log.info("Seeding price data for {} instruments", INSTRUMENTS.size)

        for ((instrumentId, config) in INSTRUMENTS) {
            seedInstrument(instrumentId, config)
        }

        log.info("Price data seeding complete")
    }

    private suspend fun seedInstrument(instrumentId: InstrumentId, config: InstrumentConfig) {
        val currency = Currency.getInstance(config.currency)
        val latestTime = LATEST_TIME
        val startPrice = config.startPrice
        val latestPrice = config.latestPrice
        val hours = 168 // 7 days of hourly data

        // Generate hourly data points interpolating from startPrice to latestPrice
        for (i in 0..hours) {
            val fraction = i.toDouble() / hours
            // Linear interpolation with small variation
            val interpolated = startPrice + (latestPrice - startPrice) * fraction
            // Add deterministic variation based on instrument + hour
            val variation = 1.0 + (instrumentId.value.hashCode() + i).rem(100) * 0.0001
            val price = BigDecimal(interpolated * variation)
                .setScale(config.scale, RoundingMode.HALF_UP)

            val timestamp = latestTime.minus((hours - i).toLong(), ChronoUnit.HOURS)
            val point = PricePoint(
                instrumentId = instrumentId,
                price = Money(price, currency),
                timestamp = timestamp,
                source = PriceSource.EXCHANGE,
            )
            repository.save(point)
        }
    }

    internal data class InstrumentConfig(
        val currency: String,
        val startPrice: Double,
        val latestPrice: Double,
        val assetClass: AssetClass,
        val scale: Int = 2,
    )

    companion object {
        val LATEST_TIME: Instant = Instant.parse("2026-02-22T10:00:00Z")

        val INSTRUMENT_IDS: Set<String> = setOf(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "EURUSD", "US2Y", "US10Y", "US30Y", "GC", "SPX-PUT-4500",
            "NVDA", "META", "JPM", "BABA",
            "GBPUSD", "USDJPY",
            "CL", "SI",
            "SPX-CALL-5000", "VIX-PUT-15",
            "DE10Y",
        )

        internal val INSTRUMENTS: Map<InstrumentId, InstrumentConfig> = mapOf(
            // Equities
            InstrumentId("AAPL") to InstrumentConfig("USD", 187.10, 189.25, AssetClass.EQUITY),
            InstrumentId("GOOGL") to InstrumentConfig("USD", 176.50, 178.90, AssetClass.EQUITY),
            InstrumentId("MSFT") to InstrumentConfig("USD", 422.30, 425.60, AssetClass.EQUITY),
            InstrumentId("AMZN") to InstrumentConfig("USD", 207.80, 210.30, AssetClass.EQUITY),
            InstrumentId("TSLA") to InstrumentConfig("USD", 244.50, 242.15, AssetClass.EQUITY),
            InstrumentId("NVDA") to InstrumentConfig("USD", 875.00, 892.50, AssetClass.EQUITY),
            InstrumentId("META") to InstrumentConfig("USD", 498.20, 508.40, AssetClass.EQUITY),
            InstrumentId("JPM") to InstrumentConfig("USD", 206.50, 211.80, AssetClass.EQUITY),
            InstrumentId("BABA") to InstrumentConfig("USD", 82.40, 86.10, AssetClass.EQUITY),
            // FX
            InstrumentId("EURUSD") to InstrumentConfig("USD", 1.0830, 1.0856, AssetClass.FX, scale = 4),
            InstrumentId("GBPUSD") to InstrumentConfig("USD", 1.2550, 1.2620, AssetClass.FX, scale = 4),
            InstrumentId("USDJPY") to InstrumentConfig("USD", 149.20, 150.80, AssetClass.FX, scale = 4),
            // Fixed Income
            InstrumentId("US2Y") to InstrumentConfig("USD", 99.30, 99.40, AssetClass.FIXED_INCOME),
            InstrumentId("US10Y") to InstrumentConfig("USD", 96.85, 97.10, AssetClass.FIXED_INCOME),
            InstrumentId("US30Y") to InstrumentConfig("USD", 92.80, 93.25, AssetClass.FIXED_INCOME),
            InstrumentId("DE10Y") to InstrumentConfig("EUR", 97.50, 98.20, AssetClass.FIXED_INCOME),
            // Commodities
            InstrumentId("GC") to InstrumentConfig("USD", 2038.20, 2058.40, AssetClass.COMMODITY),
            InstrumentId("CL") to InstrumentConfig("USD", 75.80, 78.30, AssetClass.COMMODITY),
            InstrumentId("SI") to InstrumentConfig("USD", 22.80, 23.65, AssetClass.COMMODITY),
            // Derivatives
            InstrumentId("SPX-PUT-4500") to InstrumentConfig("USD", 30.10, 28.75, AssetClass.DERIVATIVE),
            InstrumentId("SPX-CALL-5000") to InstrumentConfig("USD", 39.50, 43.80, AssetClass.DERIVATIVE),
            InstrumentId("VIX-PUT-15") to InstrumentConfig("USD", 4.10, 3.60, AssetClass.DERIVATIVE),
        )
    }
}
