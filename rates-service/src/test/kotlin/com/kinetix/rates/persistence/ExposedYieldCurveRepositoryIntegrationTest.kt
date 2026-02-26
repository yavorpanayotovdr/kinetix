package com.kinetix.rates.persistence

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.Tenor
import com.kinetix.common.model.YieldCurve
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun curve(
    curveId: String = "USD-TREASURY",
    currency: Currency = USD,
    asOf: Instant = NOW,
    tenors: List<Tenor> = listOf(
        Tenor.oneMonth(BigDecimal("0.0400")),
        Tenor.oneYear(BigDecimal("0.0500")),
    ),
    source: RateSource = RateSource.CENTRAL_BANK,
) = YieldCurve(
    curveId = curveId,
    currency = currency,
    asOf = asOf,
    tenors = tenors,
    source = source,
)

class ExposedYieldCurveRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: YieldCurveRepository = ExposedYieldCurveRepository()

    beforeEach {
        newSuspendedTransaction {
            YieldCurveTenorTable.deleteAll()
            YieldCurveTable.deleteAll()
        }
    }

    test("save and findLatest returns the curve") {
        repository.save(curve())

        val found = repository.findLatest("USD-TREASURY")
        found.shouldNotBeNull()
        found.curveId shouldBe "USD-TREASURY"
        found.currency shouldBe USD
        found.source shouldBe RateSource.CENTRAL_BANK
        found.tenors shouldHaveSize 2
        found.tenors[0].label shouldBe "1M"
        found.tenors[0].rate.compareTo(BigDecimal("0.0400")) shouldBe 0
        found.tenors[1].label shouldBe "1Y"
        found.tenors[1].rate.compareTo(BigDecimal("0.0500")) shouldBe 0
    }

    test("findLatest returns null for unknown curveId") {
        repository.findLatest("UNKNOWN").shouldBeNull()
    }

    test("findLatest returns the most recent curve when multiple exist") {
        repository.save(curve(asOf = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(curve(asOf = Instant.parse("2026-01-15T10:00:00Z")))

        val found = repository.findLatest("USD-TREASURY")
        found.shouldNotBeNull()
        found.asOf shouldBe Instant.parse("2026-01-15T10:00:00Z")
    }

    test("save preserves multi-tenor curve with correct ordering") {
        val multiTenor = curve(
            tenors = listOf(
                Tenor.oneMonth(BigDecimal("0.0400")),
                Tenor.threeMonths(BigDecimal("0.0425")),
                Tenor.sixMonths(BigDecimal("0.0450")),
                Tenor.oneYear(BigDecimal("0.0500")),
                Tenor.tenYears(BigDecimal("0.0525")),
            ),
        )
        repository.save(multiTenor)

        val found = repository.findLatest("USD-TREASURY")
        found.shouldNotBeNull()
        found.tenors shouldHaveSize 5
        found.tenors[0].days shouldBe 30
        found.tenors[4].days shouldBe 3650
    }

    test("findByTimeRange returns curves within range") {
        repository.save(curve(asOf = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(curve(asOf = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(curve(asOf = Instant.parse("2026-01-20T10:00:00Z")))

        val results = repository.findByTimeRange(
            "USD-TREASURY",
            Instant.parse("2026-01-12T00:00:00Z"),
            Instant.parse("2026-01-18T00:00:00Z"),
        )
        results shouldHaveSize 1
        results[0].asOf shouldBe Instant.parse("2026-01-15T10:00:00Z")
    }

    test("findByTimeRange returns empty list for no matches") {
        repository.save(curve())

        val results = repository.findByTimeRange(
            "USD-TREASURY",
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2027-12-31T00:00:00Z"),
        )
        results shouldHaveSize 0
    }

    test("findByTimeRange returns curves ordered by asOfDate ascending") {
        repository.save(curve(asOf = Instant.parse("2026-01-20T10:00:00Z")))
        repository.save(curve(asOf = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(curve(asOf = Instant.parse("2026-01-15T10:00:00Z")))

        val results = repository.findByTimeRange(
            "USD-TREASURY",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-31T00:00:00Z"),
        )
        results shouldHaveSize 3
        results[0].asOf shouldBe Instant.parse("2026-01-10T10:00:00Z")
        results[1].asOf shouldBe Instant.parse("2026-01-15T10:00:00Z")
        results[2].asOf shouldBe Instant.parse("2026-01-20T10:00:00Z")
    }
})
