package com.kinetix.referencedata.cache

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class RedisReferenceDataCacheIntegrationTest : FunSpec({

    val connection = RedisTestSetup.start()
    val cache: ReferenceDataCache = RedisReferenceDataCache(connection)

    beforeEach {
        connection.sync().flushall()
    }

    test("put and get dividend yield") {
        val yield = DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0065,
            exDate = LocalDate.of(2026, 3, 15),
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            source = ReferenceDataSource.BLOOMBERG,
        )
        cache.putDividendYield(yield)

        val found = cache.getDividendYield(InstrumentId("AAPL"))
        found.shouldNotBeNull()
        found.instrumentId shouldBe InstrumentId("AAPL")
        found.yield shouldBe 0.0065
        found.source shouldBe ReferenceDataSource.BLOOMBERG
    }

    test("get dividend yield returns null for unknown instrument") {
        cache.getDividendYield(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("put and get credit spread") {
        val spread = CreditSpread(
            instrumentId = InstrumentId("CORP-BOND-1"),
            spread = 0.0125,
            rating = "AA",
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            source = ReferenceDataSource.RATING_AGENCY,
        )
        cache.putCreditSpread(spread)

        val found = cache.getCreditSpread(InstrumentId("CORP-BOND-1"))
        found.shouldNotBeNull()
        found.instrumentId shouldBe InstrumentId("CORP-BOND-1")
        found.spread shouldBe 0.0125
        found.rating shouldBe "AA"
    }

    test("get credit spread returns null for unknown instrument") {
        cache.getCreditSpread(InstrumentId("UNKNOWN")).shouldBeNull()
    }
})
