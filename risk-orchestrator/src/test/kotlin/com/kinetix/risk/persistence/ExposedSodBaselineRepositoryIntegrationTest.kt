package com.kinetix.risk.persistence

import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaseline
import com.kinetix.risk.model.SodGreekSnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val PORTFOLIO = BookId("port-1")
private val TODAY = LocalDate.of(2025, 1, 15)
private val YESTERDAY = LocalDate.of(2025, 1, 14)
private val NOW = Instant.parse("2025-01-15T08:00:00Z")

private fun baseline(
    bookId: BookId = PORTFOLIO,
    baselineDate: LocalDate = TODAY,
    snapshotType: SnapshotType = SnapshotType.MANUAL,
    createdAt: Instant = NOW,
) = SodBaseline(
    bookId = bookId,
    baselineDate = baselineDate,
    snapshotType = snapshotType,
    createdAt = createdAt,
)

class ExposedSodBaselineRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: SodBaselineRepository = ExposedSodBaselineRepository(db)
    val greekSnapshotRepository: SodGreekSnapshotRepository = ExposedSodGreekSnapshotRepository(db)

    beforeEach {
        // Delete baselines first (FK child), then greek snapshots (FK parent)
        newSuspendedTransaction(db = db) {
            SodBaselinesTable.deleteAll()
            SodGreekSnapshotsTable.deleteAll()
        }
    }

    test("saves and retrieves a baseline by portfolio and date") {
        val bl = baseline()
        repository.save(bl)

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.bookId shouldBe PORTFOLIO
        found.baselineDate shouldBe TODAY
        found.snapshotType shouldBe SnapshotType.MANUAL
        found.createdAt shouldBe NOW
    }

    test("returns null for unknown portfolio and date") {
        repository.findByBookIdAndDate(BookId("unknown"), TODAY).shouldBeNull()
    }

    test("upserts on same portfolio-date key") {
        repository.save(baseline(snapshotType = SnapshotType.AUTO, createdAt = NOW))
        repository.save(baseline(snapshotType = SnapshotType.MANUAL, createdAt = Instant.parse("2025-01-15T09:00:00Z")))

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.snapshotType shouldBe SnapshotType.MANUAL
        found.createdAt shouldBe Instant.parse("2025-01-15T09:00:00Z")
    }

    test("deletes baseline by portfolio and date") {
        repository.save(baseline())

        repository.deleteByBookIdAndDate(PORTFOLIO, TODAY)

        repository.findByBookIdAndDate(PORTFOLIO, TODAY).shouldBeNull()
    }

    test("delete is safe when no baseline exists") {
        repository.deleteByBookIdAndDate(PORTFOLIO, TODAY)
        // no exception thrown
    }

    test("different dates are stored independently") {
        repository.save(baseline(baselineDate = TODAY))
        repository.save(baseline(baselineDate = YESTERDAY))

        repository.findByBookIdAndDate(PORTFOLIO, TODAY).shouldNotBeNull()
        repository.findByBookIdAndDate(PORTFOLIO, YESTERDAY).shouldNotBeNull()
    }

    test("different portfolios are stored independently") {
        val other = BookId("port-2")
        repository.save(baseline(bookId = PORTFOLIO))
        repository.save(baseline(bookId = other))

        repository.findByBookIdAndDate(PORTFOLIO, TODAY).shouldNotBeNull()
        repository.findByBookIdAndDate(other, TODAY).shouldNotBeNull()
    }

    test("saves and retrieves baseline with job metadata") {
        val jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val bl = baseline().copy(sourceJobId = jobId, calculationType = "PARAMETRIC")
        repository.save(bl)

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.sourceJobId shouldBe jobId
        found.calculationType shouldBe "PARAMETRIC"
    }

    test("saves and retrieves baseline with null job metadata") {
        repository.save(baseline())

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.sourceJobId shouldBe null
        found.calculationType shouldBe null
    }

    test("greekSnapshotId is null when not set") {
        repository.save(baseline())

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.greekSnapshotId shouldBe null
    }

    test("saves and retrieves baseline with a greek snapshot ID") {
        // Insert a greek snapshot row to satisfy the FK constraint
        val greekSnapshot = SodGreekSnapshot(
            bookId = PORTFOLIO,
            snapshotDate = TODAY,
            instrumentId = InstrumentId("AAPL"),
            sodPrice = BigDecimal("185.50"),
            delta = 0.55,
            createdAt = NOW,
        )
        greekSnapshotRepository.saveAll(listOf(greekSnapshot))
        val savedSnapshot = greekSnapshotRepository.findByInstrumentAndDate(PORTFOLIO, InstrumentId("AAPL"), TODAY)
        val greekSnapshotId = savedSnapshot!!.id!!

        val bl = baseline().copy(greekSnapshotId = greekSnapshotId)
        repository.save(bl)

        val found = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        found.shouldNotBeNull()
        found.greekSnapshotId shouldBe greekSnapshotId
    }

    test("upsert overwrites greekSnapshotId when updated") {
        // Insert a greek snapshot to satisfy the FK
        val greekSnapshot = SodGreekSnapshot(
            bookId = PORTFOLIO,
            snapshotDate = TODAY,
            instrumentId = InstrumentId("MSFT"),
            sodPrice = BigDecimal("420.00"),
            delta = 0.60,
            createdAt = NOW,
        )
        greekSnapshotRepository.saveAll(listOf(greekSnapshot))
        val savedSnapshot = greekSnapshotRepository.findByInstrumentAndDate(PORTFOLIO, InstrumentId("MSFT"), TODAY)
        val greekSnapshotId = savedSnapshot!!.id!!

        // First save without a greek snapshot ID
        repository.save(baseline())
        val beforeUpsert = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        beforeUpsert.shouldNotBeNull()
        beforeUpsert.greekSnapshotId shouldBe null

        // Upsert with the greek snapshot ID now linked
        repository.save(baseline().copy(greekSnapshotId = greekSnapshotId))

        val afterUpsert = repository.findByBookIdAndDate(PORTFOLIO, TODAY)
        afterUpsert.shouldNotBeNull()
        afterUpsert.greekSnapshotId shouldBe greekSnapshotId
    }
})
