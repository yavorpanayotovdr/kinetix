package com.kinetix.marketdata.seed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.marketdata.persistence.MarketDataRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class DevDataSeederTest : FunSpec({

    val repository = mockk<MarketDataRepository>()
    val seeder = DevDataSeeder(repository)

    beforeEach {
        clearMocks(repository)
    }

    test("seeds data when database is empty") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { repository.save(any()) } just runs

        seeder.seed()

        // 22 instruments × 169 data points each (0..168 hours inclusive)
        coVerify(exactly = 22 * 169) { repository.save(any()) }
    }

    test("skips seeding when data already exists") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns MarketDataPoint(
            instrumentId = InstrumentId("AAPL"),
            price = com.kinetix.common.model.Money(java.math.BigDecimal("189.25"), java.util.Currency.getInstance("USD")),
            timestamp = Instant.now(),
            source = com.kinetix.common.model.MarketDataSource.EXCHANGE,
        )

        seeder.seed()

        coVerify(exactly = 0) { repository.save(any()) }
    }

    test("instruments list contains all 22 expected instruments") {
        DevDataSeeder.INSTRUMENT_IDS shouldBe setOf(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "EURUSD", "US2Y", "US10Y", "US30Y", "GC", "SPX-PUT-4500",
            "NVDA", "META", "JPM", "BABA",
            "GBPUSD", "USDJPY",
            "CL", "SI",
            "SPX-CALL-5000", "VIX-PUT-15",
            "DE10Y",
        )
    }

    test("saved points have timestamps spanning 7 days") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        val savedPoints = mutableListOf<MarketDataPoint>()
        coEvery { repository.save(capture(savedPoints)) } just runs

        seeder.seed()

        // Check AAPL points span 168 hours (7 days)
        val aaplPoints = savedPoints.filter { it.instrumentId.value == "AAPL" }
            .sortedBy { it.timestamp }
        aaplPoints.size shouldBe 169
        val duration = java.time.Duration.between(aaplPoints.first().timestamp, aaplPoints.last().timestamp)
        duration.toHours() shouldBe 168
    }
})
