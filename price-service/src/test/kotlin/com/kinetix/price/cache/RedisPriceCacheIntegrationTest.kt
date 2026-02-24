package com.kinetix.price.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun point(
    instrumentId: String = "AAPL",
    priceAmount: BigDecimal = BigDecimal("150.00"),
    currency: Currency = USD,
    timestamp: Instant = Instant.parse("2025-01-15T10:00:00Z"),
    source: PriceSource = PriceSource.EXCHANGE,
) = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(priceAmount, currency),
    timestamp = timestamp,
    source = source,
)

class RedisPriceCacheIntegrationTest : FunSpec({

    val connection = RedisTestSetup.start()
    val cache: PriceCache = RedisPriceCache(connection)

    beforeEach {
        connection.sync().flushall()
    }

    test("put and get price point") {
        val p = point()
        cache.put(p)

        val found = cache.get(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.instrumentId shouldBe InstrumentId("AAPL")
        found.price.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        found.price.currency shouldBe USD
        found.timestamp shouldBe Instant.parse("2025-01-15T10:00:00Z")
        found.source shouldBe PriceSource.EXCHANGE
    }

    test("get returns null for unknown instrument") {
        cache.get(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("put overwrites previous value for same instrument") {
        cache.put(point(priceAmount = BigDecimal("150.00")))
        cache.put(point(priceAmount = BigDecimal("155.00"), timestamp = Instant.parse("2025-01-15T11:00:00Z")))

        val found = cache.get(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.price.amount.compareTo(BigDecimal("155.00")) shouldBe 0
        found.timestamp shouldBe Instant.parse("2025-01-15T11:00:00Z")
    }

    test("caches different instruments independently") {
        cache.put(point(instrumentId = "AAPL", priceAmount = BigDecimal("150.00")))
        cache.put(point(instrumentId = "MSFT", priceAmount = BigDecimal("400.00")))

        val aapl = cache.get(InstrumentId("AAPL"))
        val msft = cache.get(InstrumentId("MSFT"))
        aapl.shouldNotBeNull()
        msft.shouldNotBeNull()
        aapl.price.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        msft.price.amount.compareTo(BigDecimal("400.00")) shouldBe 0
    }

    test("preserves BigDecimal precision") {
        val p = point(priceAmount = BigDecimal("98765.432109876543"))
        cache.put(p)

        val found = cache.get(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.price.amount.compareTo(BigDecimal("98765.432109876543")) shouldBe 0
    }

    test("preserves all price sources") {
        PriceSource.entries.forEach { src ->
            cache.put(point(instrumentId = src.name, source = src))
            val found = cache.get(InstrumentId(src.name))
            found.shouldNotBeNull()
            found.source shouldBe src
        }
    }
})
