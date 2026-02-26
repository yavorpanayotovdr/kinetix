package com.kinetix.volatility.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant

private val AAPL = InstrumentId("AAPL")
private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun surface(
    instrumentId: InstrumentId = AAPL,
    asOf: Instant = NOW,
    points: List<VolPoint> = listOf(
        VolPoint(BigDecimal("100"), 30, BigDecimal("0.25")),
        VolPoint(BigDecimal("100"), 90, BigDecimal("0.28")),
    ),
    source: VolatilitySource = VolatilitySource.BLOOMBERG,
) = VolSurface(
    instrumentId = instrumentId,
    asOf = asOf,
    points = points,
    source = source,
)

class ExposedVolSurfaceRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: VolSurfaceRepository = ExposedVolSurfaceRepository()

    beforeEach {
        newSuspendedTransaction {
            VolPointTable.deleteAll()
            VolSurfaceTable.deleteAll()
        }
    }

    test("save and findLatest returns the surface") {
        repository.save(surface())

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.instrumentId shouldBe AAPL
        found.source shouldBe VolatilitySource.BLOOMBERG
        found.points shouldHaveSize 2
        found.points[0].strike.compareTo(BigDecimal("100")) shouldBe 0
        found.points[0].maturityDays shouldBe 30
        found.points[0].impliedVol.compareTo(BigDecimal("0.25")) shouldBe 0
    }

    test("findLatest returns null for unknown instrument") {
        repository.findLatest(InstrumentId("UNKNOWN")).shouldBeNull()
    }

    test("findLatest returns the most recent surface") {
        repository.save(surface(asOf = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(surface(asOf = Instant.parse("2026-01-15T10:00:00Z")))

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.asOf shouldBe Instant.parse("2026-01-15T10:00:00Z")
    }

    test("save preserves multi-point surface") {
        val multiPoint = surface(
            points = listOf(
                VolPoint(BigDecimal("90"), 30, BigDecimal("0.30")),
                VolPoint(BigDecimal("100"), 30, BigDecimal("0.25")),
                VolPoint(BigDecimal("110"), 30, BigDecimal("0.27")),
                VolPoint(BigDecimal("90"), 90, BigDecimal("0.32")),
                VolPoint(BigDecimal("100"), 90, BigDecimal("0.28")),
                VolPoint(BigDecimal("110"), 90, BigDecimal("0.29")),
            ),
        )
        repository.save(multiPoint)

        val found = repository.findLatest(AAPL)
        found.shouldNotBeNull()
        found.points shouldHaveSize 6
    }

    test("findByTimeRange returns surfaces within range") {
        repository.save(surface(asOf = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(surface(asOf = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(surface(asOf = Instant.parse("2026-01-20T10:00:00Z")))

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

    test("findByTimeRange includes surfaces with their points") {
        repository.save(surface(asOf = Instant.parse("2026-01-15T10:00:00Z")))

        val results = repository.findByTimeRange(
            AAPL,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-31T00:00:00Z"),
        )
        results shouldHaveSize 1
        results[0].points shouldHaveSize 2
    }
})
