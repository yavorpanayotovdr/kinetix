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

        // 11 instruments × 25 data points each (0..24 hours inclusive)
        coVerify(exactly = 11 * 25) { repository.save(any()) }
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

    test("instruments list contains all 11 expected instruments") {
        DevDataSeeder.INSTRUMENT_IDS shouldBe setOf(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "EURUSD", "US2Y", "US10Y", "US30Y", "GC", "SPX-PUT-4500",
        )
    }

    test("saved points have timestamps spanning 24 hours") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        val savedPoints = mutableListOf<MarketDataPoint>()
        coEvery { repository.save(capture(savedPoints)) } just runs

        seeder.seed()

        // Check AAPL points span 24 hours
        val aaplPoints = savedPoints.filter { it.instrumentId.value == "AAPL" }
            .sortedBy { it.timestamp }
        aaplPoints.size shouldBe 25
        val duration = java.time.Duration.between(aaplPoints.first().timestamp, aaplPoints.last().timestamp)
        duration.toHours() shouldBe 24
    }
})
