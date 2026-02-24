package com.kinetix.price.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import com.kinetix.price.cache.PriceCache
import com.kinetix.price.kafka.PricePublisher
import com.kinetix.price.persistence.PriceRepository
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
    source: PriceSource = PriceSource.EXCHANGE,
) = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(priceAmount, USD),
    timestamp = timestamp,
    source = source,
)

class PriceIngestionServiceTest : FunSpec({

    val repository = mockk<PriceRepository>()
    val cache = mockk<PriceCache>()
    val publisher = mockk<PricePublisher>()
    val service = PriceIngestionService(repository, cache, publisher)

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

    test("ingest works with different price sources") {
        PriceSource.entries.forEach { src ->
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
