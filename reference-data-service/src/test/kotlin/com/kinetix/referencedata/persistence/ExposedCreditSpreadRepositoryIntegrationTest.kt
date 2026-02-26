package com.kinetix.referencedata.persistence

import com.kinetix.common.model.CreditSpread
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

private val BOND = InstrumentId("CORP-BOND-1")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun creditSpread(
    instrumentId: InstrumentId = BOND,
    spread: Double = 0.0125,
    rating: String? = "AA",
    asOfDate: Instant = NOW,
    source: ReferenceDataSource = ReferenceDataSource.RATING_AGENCY,
) = CreditSpread(
    instrumentId = instrumentId,
    spread = spread,
    rating = rating,
    asOfDate = asOfDate,
    source = source,
)

class ExposedCreditSpreadRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: CreditSpreadRepository = ExposedCreditSpreadRepository()

    beforeEach {
        newSuspendedTransaction { CreditSpreadTable.deleteAll() }
    }

    test("save and findLatest returns the credit spread") {
        repository.save(creditSpread())

        val found = repository.findLatest(BOND)
        found.shouldNotBeNull()
        found.instrumentId shouldBe BOND
        found.spread shouldBe 0.0125
        found.rating shouldBe "AA"
        found.source shouldBe ReferenceDataSource.RATING_AGENCY
    }

    test("findLatest returns null for unknown instrument") {
        repository.findLatest(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("save handles null rating") {
        repository.save(creditSpread(rating = null))

        val found = repository.findLatest(BOND)
        found.shouldNotBeNull()
        found.rating.shouldBeNull()
    }

    test("findByTimeRange returns spreads within range") {
        repository.save(creditSpread(asOfDate = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(creditSpread(asOfDate = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(creditSpread(asOfDate = Instant.parse("2026-01-20T10:00:00Z")))

        val results = repository.findByTimeRange(
            BOND,
            Instant.parse("2026-01-12T00:00:00Z"),
            Instant.parse("2026-01-18T00:00:00Z"),
        )
        results shouldHaveSize 1
    }
})
