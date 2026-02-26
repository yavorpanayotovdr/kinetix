package com.kinetix.correlation.persistence

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private val NOW = Instant.parse("2026-01-15T10:00:00Z")
private val LABELS = listOf("AAPL", "MSFT")
private val VALUES_2X2 = listOf(1.0, 0.65, 0.65, 1.0)

private fun matrix(
    labels: List<String> = LABELS,
    values: List<Double> = VALUES_2X2,
    windowDays: Int = 252,
    asOfDate: Instant = NOW,
    method: EstimationMethod = EstimationMethod.HISTORICAL,
) = CorrelationMatrix(
    labels = labels,
    values = values,
    windowDays = windowDays,
    asOfDate = asOfDate,
    method = method,
)

class ExposedCorrelationMatrixRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: CorrelationMatrixRepository = ExposedCorrelationMatrixRepository()

    beforeEach {
        newSuspendedTransaction { CorrelationMatrixTable.deleteAll() }
    }

    test("save and findLatest returns the matrix") {
        repository.save(matrix())

        val found = repository.findLatest(LABELS, 252)
        found.shouldNotBeNull()
        found.labels shouldBe LABELS
        found.values shouldBe VALUES_2X2
        found.windowDays shouldBe 252
        found.method shouldBe EstimationMethod.HISTORICAL
    }

    test("findLatest returns null for unknown labels") {
        repository.findLatest(listOf("X", "Y"), 252).shouldBeNull()
    }

    test("findLatest returns the most recent matrix") {
        repository.save(matrix(asOfDate = Instant.parse("2026-01-10T10:00:00Z"), values = listOf(1.0, 0.5, 0.5, 1.0)))
        repository.save(matrix(asOfDate = Instant.parse("2026-01-15T10:00:00Z"), values = listOf(1.0, 0.65, 0.65, 1.0)))

        val found = repository.findLatest(LABELS, 252)
        found.shouldNotBeNull()
        found.values shouldBe listOf(1.0, 0.65, 0.65, 1.0)
    }

    test("findByTimeRange returns matrices within range") {
        repository.save(matrix(asOfDate = Instant.parse("2026-01-10T10:00:00Z")))
        repository.save(matrix(asOfDate = Instant.parse("2026-01-15T10:00:00Z")))
        repository.save(matrix(asOfDate = Instant.parse("2026-01-20T10:00:00Z")))

        val results = repository.findByTimeRange(
            LABELS, 252,
            Instant.parse("2026-01-12T00:00:00Z"),
            Instant.parse("2026-01-18T00:00:00Z"),
        )
        results shouldHaveSize 1
    }

    test("findByTimeRange returns empty list for no matches") {
        val results = repository.findByTimeRange(
            LABELS, 252,
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2027-12-31T00:00:00Z"),
        )
        results shouldHaveSize 0
    }

    test("save and retrieve preserves 3x3 matrix values") {
        val labels3 = listOf("AAPL", "GOOG", "MSFT")
        val values3 = listOf(1.0, 0.7, 0.65, 0.7, 1.0, 0.8, 0.65, 0.8, 1.0)
        repository.save(matrix(labels = labels3, values = values3))

        val found = repository.findLatest(labels3, 252)
        found.shouldNotBeNull()
        found.labels shouldBe labels3
        found.values shouldBe values3
    }
})
