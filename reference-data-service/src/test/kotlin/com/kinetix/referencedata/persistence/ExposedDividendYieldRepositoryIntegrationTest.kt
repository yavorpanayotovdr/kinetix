package com.kinetix.referencedata.persistence

import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate

private val AAPL = InstrumentId("AAPL")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun dividendYield(
    instrumentId: InstrumentId = AAPL,
    yield: Double = 0.0065,
    exDate: LocalDate? = LocalDate.of(2026, 3, 15),
    asOfDate: Instant = NOW,
    source: ReferenceDataSource = ReferenceDataSource.BLOOMBERG,
) = DividendYield(
    instrumentId = instrumentId,
    yield = yield,
    exDate = exDate,
    asOfDate = asOfDate,
    source = source,
)

class ExposedDividendYieldRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: DividendYieldRepository = ExposedDividendYieldRepository()

    beforeEach {
        newSuspendedTransaction { DividendYieldTable.deleteAll() }
    }

    test("save and findLatest returns the dividend yield") {
        repository.save(dividendYield())

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.instrumentId shouldBe AAPL
        found.yield shouldBe 0.0065
        found.exDate shouldBe LocalDate.of(2026, 3, 15)
        found.source shouldBe ReferenceDataSource.BLOOMBERG
    }

    test("findLatest returns null for unknown instrument") {
        repository.findLatest(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("findLatest returns the most recent yield") {
        repository.save(dividendYield(asOfDate = Instant.parse("2026-01-10T10:00:00Z"), yield = 0.006))
        repository.save(dividendYield(asOfDate = Instant.parse("2026-01-15T10:00:00Z"), yield = 0.0065))

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.yield shouldBe 0.0065
    }

    test("save handles null exDate") {
        repository.save(dividendYield(exDate = null))

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.exDate.shouldBeNull()
    }

    test("findByTimeRange returns yields within range") {
        repository.save(dividendYield(asOfDate = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(dividendYield(asOfDate = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(dividendYield(asOfDate = Instant.parse("2026-01-20T10:00:00Z")))

        val results = repository.findByTimeRange(
            AAPL,
            Instant.parse("2026-01-12T00:00:00Z"),
            Instant.parse("2026-01-18T00:00:00Z"),
        )
        results shouldHaveSize 1
    }

    test("findByTimeRange returns empty list for no matches") {
        val results = repository.findByTimeRange(
            AAPL,
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2027-12-31T00:00:00Z"),
        )
        results shouldHaveSize 0
    }
})
