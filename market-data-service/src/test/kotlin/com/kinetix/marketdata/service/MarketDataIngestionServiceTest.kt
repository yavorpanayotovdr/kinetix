package com.kinetix.marketdata.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import com.kinetix.marketdata.cache.MarketDataCache
import com.kinetix.marketdata.kafka.MarketDataPublisher
import com.kinetix.marketdata.persistence.MarketDataRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun point(
    instrumentId: String = "AAPL",
    priceAmount: BigDecimal = BigDecimal("150.00"),
    timestamp: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    source: MarketDataSource = MarketDataSource.EXCHANGE,
) = MarketDataPoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(priceAmount, USD),
    timestamp = timestamp,
    source = source,
)

class MarketDataIngestionServiceTest : FunSpec({

    val repository = mockk<MarketDataRepository>()
    val cache = mockk<MarketDataCache>()
    val publisher = mockk<MarketDataPublisher>()
    val service = MarketDataIngestionService(repository, cache, publisher)

    test("ingest saves to repository, caches, and publishes") {
        val p = point()
        coEvery { repository.save(p) } returns Unit
        coEvery { cache.put(p) } returns Unit
        coEvery { publisher.publish(p) } returns Unit

        service.ingest(p)

        coVerify(exactly = 1) { repository.save(p) }
        coVerify(exactly = 1) { cache.put(p) }
        coVerify(exactly = 1) { publisher.publish(p) }
    }

    test("ingest calls repository, cache, and publisher in order") {
        val p = point()
        coEvery { repository.save(p) } returns Unit
        coEvery { cache.put(p) } returns Unit
        coEvery { publisher.publish(p) } returns Unit

        service.ingest(p)

        coVerifyOrder {
            repository.save(p)
            cache.put(p)
            publisher.publish(p)
        }
    }

    test("ingest works with different market data sources") {
        MarketDataSource.entries.forEach { src ->
            val p = point(instrumentId = src.name, source = src)
            coEvery { repository.save(p) } returns Unit
            coEvery { cache.put(p) } returns Unit
            coEvery { publisher.publish(p) } returns Unit

            service.ingest(p)

            coVerify { repository.save(p) }
            coVerify { cache.put(p) }
            coVerify { publisher.publish(p) }
        }
    }
})
