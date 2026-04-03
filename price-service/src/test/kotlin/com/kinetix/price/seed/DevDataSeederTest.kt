package com.kinetix.price.seed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.price.persistence.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class DevDataSeederTest : FunSpec({

    val repository = mockk<PriceRepository>()
    val seeder = DevDataSeeder(repository)

    beforeEach {
        clearMocks(repository)
    }

    test("seeds data when database is empty") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        coEvery { repository.save(any()) } just runs

        seeder.seed()

        // 22 instruments × 169 hourly points
        // + 22 instruments × 253 daily closes (day 252 downTo 0, inclusive)
        // + 301 daily prices for IDX-SPX benchmark (day 300 downTo 0, inclusive)
        val expectedSaves = 22 * 169 + 22 * 253 + 301
        coVerify(exactly = expectedSaves) { repository.save(any()) }
    }

    test("skips seeding when data already exists") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns PricePoint(
            instrumentId = InstrumentId("AAPL"),
            price = com.kinetix.common.model.Money(java.math.BigDecimal("189.25"), java.util.Currency.getInstance("USD")),
            timestamp = Instant.now(),
            source = com.kinetix.common.model.PriceSource.EXCHANGE,
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

    test("AAPL has 169 hourly and 253 daily saved points") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        val savedPoints = mutableListOf<PricePoint>()
        coEvery { repository.save(capture(savedPoints)) } just runs

        seeder.seed()

        val aaplPoints = savedPoints.filter { it.instrumentId.value == "AAPL" }
        // 169 hourly + 253 daily (day 252 downTo 0, inclusive)
        aaplPoints.size shouldBe 169 + 253
    }

    test("all daily close prices are strictly positive") {
        coEvery { repository.findLatest(InstrumentId("AAPL")) } returns null
        val savedPoints = mutableListOf<PricePoint>()
        coEvery { repository.save(capture(savedPoints)) } just runs

        seeder.seed()

        // Daily closes are for timestamps older than 7 days (outside the hourly window)
        val dailyCutoff = DevDataSeeder.LATEST_TIME.minus(7, java.time.temporal.ChronoUnit.DAYS)
        val dailyPoints = savedPoints.filter { it.timestamp.isBefore(dailyCutoff) }
        dailyPoints.forEach { point ->
            (point.price.amount > java.math.BigDecimal.ZERO) shouldBe true
        }
    }
})
