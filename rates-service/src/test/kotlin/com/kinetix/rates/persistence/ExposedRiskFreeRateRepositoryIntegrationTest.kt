package com.kinetix.rates.persistence

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.RiskFreeRate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun rate(
    currency: Currency = USD,
    tenor: String = "3M",
    rate: Double = 0.0525,
    asOfDate: Instant = NOW,
    source: RateSource = RateSource.CENTRAL_BANK,
) = RiskFreeRate(
    currency = currency,
    tenor = tenor,
    rate = rate,
    asOfDate = asOfDate,
    source = source,
)

class ExposedRiskFreeRateRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: RiskFreeRateRepository = ExposedRiskFreeRateRepository()

    beforeEach {
        newSuspendedTransaction { RiskFreeRateTable.deleteAll() }
    }

    test("save and findLatest returns the rate") {
        repository.save(rate())

        val found = repository.findLatest(USD, "3M")
        found.shouldNotBeNull()
        found.currency shouldBe USD
        found.tenor shouldBe "3M"
        found.rate shouldBe 0.0525
        found.source shouldBe RateSource.CENTRAL_BANK
    }

    test("findLatest returns null for unknown currency and tenor") {
        repository.findLatest(Currency.getInstance("JPY"), "O/N").shouldBeNull()
    }

    test("findLatest returns the most recent rate") {
        repository.save(rate(asOfDate = Instant.parse("2026-01-10T10:00:00Z"), rate = 0.05))
        repository.save(rate(asOfDate = Instant.parse("2026-01-15T10:00:00Z"), rate = 0.0525))

        val found = repository.findLatest(USD, "3M")
        found.shouldNotBeNull()
        found.rate shouldBe 0.0525
    }

    test("findByTimeRange returns rates within range") {
        repository.save(rate(asOfDate = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(rate(asOfDate = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(rate(asOfDate = Instant.parse("2026-01-20T10:00:00Z")))

        val results = repository.findByTimeRange(
            USD, "3M",
            Instant.parse("2026-01-12T00:00:00Z"),
            Instant.parse("2026-01-18T00:00:00Z"),
        )
        results shouldHaveSize 1
    }

    test("findByTimeRange returns empty list for no matches") {
        val results = repository.findByTimeRange(
            USD, "3M",
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2027-12-31T00:00:00Z"),
        )
        results shouldHaveSize 0
    }
})
