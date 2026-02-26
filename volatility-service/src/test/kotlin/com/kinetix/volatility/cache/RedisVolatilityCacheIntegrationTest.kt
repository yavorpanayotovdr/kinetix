package com.kinetix.volatility.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant

class RedisVolatilityCacheIntegrationTest : FunSpec({

    val connection = RedisTestSetup.start()
    val cache: VolatilityCache = RedisVolatilityCache(connection)

    beforeEach {
        connection.sync().flushall()
    }

    test("put and get surface") {
        val surface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-01-15T10:00:00Z"),
            points = listOf(
                VolPoint(BigDecimal("100"), 30, BigDecimal("0.25")),
                VolPoint(BigDecimal("100"), 90, BigDecimal("0.28")),
            ),
            source = VolatilitySource.BLOOMBERG,
        )
        cache.putSurface(surface)

        val found = cache.getSurface(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.instrumentId shouldBe InstrumentId("AAPL")
        found.points shouldHaveSize 2
        found.source shouldBe VolatilitySource.BLOOMBERG
    }

    test("get returns null for unknown instrument") {
        cache.getSurface(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("put overwrites previous surface") {
        val surface1 = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-01-15T10:00:00Z"),
            points = listOf(VolPoint(BigDecimal("100"), 30, BigDecimal("0.25"))),
            source = VolatilitySource.BLOOMBERG,
        )
        cache.putSurface(surface1)

        val surface2 = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-01-16T10:00:00Z"),
            points = listOf(VolPoint(BigDecimal("100"), 30, BigDecimal("0.30"))),
            source = VolatilitySource.EXCHANGE,
        )
        cache.putSurface(surface2)

        val found = cache.getSurface(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.source shouldBe VolatilitySource.EXCHANGE
    }

    test("caches different instruments independently") {
        val aaplSurface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = Instant.parse("2026-01-15T10:00:00Z"),
            points = listOf(VolPoint(BigDecimal("100"), 30, BigDecimal("0.25"))),
            source = VolatilitySource.BLOOMBERG,
        )
        val msftSurface = VolSurface(
            instrumentId = InstrumentId("MSFT"),
            asOf = Instant.parse("2026-01-15T10:00:00Z"),
            points = listOf(VolPoint(BigDecimal("200"), 60, BigDecimal("0.22"))),
            source = VolatilitySource.EXCHANGE,
        )
        cache.putSurface(aaplSurface)
        cache.putSurface(msftSurface)

        val aapl = cache.getSurface(InstrumentId("AAPL"))
        val msft = cache.getSurface(InstrumentId("MSFT"))
        aapl.shouldNotBeNull()
        msft.shouldNotBeNull()
        aapl.instrumentId shouldBe InstrumentId("AAPL")
        msft.instrumentId shouldBe InstrumentId("MSFT")
    }
})
