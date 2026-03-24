package com.kinetix.risk.persistence

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyRiskSnapshot
import com.kinetix.risk.model.RiskContributor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ExposedRiskHierarchySnapshotRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: RiskHierarchySnapshotRepository = ExposedRiskHierarchySnapshotRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { RiskHierarchySnapshotsTable.deleteAll() }
    }

    test("saves and retrieves a firm-level snapshot") {
        val snapshot = HierarchyRiskSnapshot(
            snapshotAt = Instant.parse("2026-03-24T12:00:00Z"),
            level = HierarchyLevel.FIRM,
            entityId = "FIRM",
            parentId = null,
            varValue = 5_000_000.0,
            expectedShortfall = 7_000_000.0,
            pnlToday = 250_000.0,
            limitUtilisation = 0.62,
            marginalVar = null,
            topContributors = listOf(
                RiskContributor("div-rates", "Rates Division", 2_100_000.0, 42.0),
                RiskContributor("div-equities", "Equities Division", 1_800_000.0, 36.0),
            ),
            isPartial = false,
        )

        repository.save(snapshot)

        val retrieved = repository.findLatest(HierarchyLevel.FIRM, "FIRM")

        retrieved shouldNotBe null
        retrieved!!.level shouldBe HierarchyLevel.FIRM
        retrieved.entityId shouldBe "FIRM"
        retrieved.parentId shouldBe null
        retrieved.varValue.shouldBeWithinPercentageOf(5_000_000.0, 0.01)
        retrieved.expectedShortfall!!.shouldBeWithinPercentageOf(7_000_000.0, 0.01)
        retrieved.pnlToday!!.shouldBeWithinPercentageOf(250_000.0, 0.01)
        retrieved.limitUtilisation!!.shouldBeWithinPercentageOf(0.62, 0.01)
        retrieved.isPartial shouldBe false
        retrieved.topContributors shouldHaveSize 2
        retrieved.topContributors[0].entityId shouldBe "div-rates"
        retrieved.topContributors[0].varContribution.shouldBeWithinPercentageOf(2_100_000.0, 0.01)
    }

    test("saves and retrieves a desk-level snapshot with parent") {
        val snapshot = HierarchyRiskSnapshot(
            snapshotAt = Instant.parse("2026-03-24T12:00:00Z"),
            level = HierarchyLevel.DESK,
            entityId = "desk-rates-govts",
            parentId = "div-rates",
            varValue = 1_250_000.0,
            expectedShortfall = null,
            pnlToday = null,
            limitUtilisation = 0.78,
            marginalVar = 950_000.0,
            topContributors = emptyList(),
            isPartial = false,
        )

        repository.save(snapshot)

        val retrieved = repository.findLatest(HierarchyLevel.DESK, "desk-rates-govts")

        retrieved shouldNotBe null
        retrieved!!.parentId shouldBe "div-rates"
        retrieved.marginalVar!!.shouldBeWithinPercentageOf(950_000.0, 0.01)
        retrieved.limitUtilisation!!.shouldBeWithinPercentageOf(0.78, 0.01)
    }

    test("findLatest returns the most recent snapshot when multiple exist") {
        val older = HierarchyRiskSnapshot(
            snapshotAt = Instant.parse("2026-03-24T08:00:00Z"),
            level = HierarchyLevel.DIVISION,
            entityId = "div-equities",
            parentId = "FIRM",
            varValue = 1_000_000.0,
            expectedShortfall = null,
            pnlToday = null,
            limitUtilisation = null,
            marginalVar = null,
            topContributors = emptyList(),
        )
        val newer = older.copy(
            snapshotAt = Instant.parse("2026-03-24T14:00:00Z"),
            varValue = 1_200_000.0,
        )

        repository.save(older)
        repository.save(newer)

        val retrieved = repository.findLatest(HierarchyLevel.DIVISION, "div-equities")

        retrieved shouldNotBe null
        retrieved!!.varValue.shouldBeWithinPercentageOf(1_200_000.0, 0.01)
    }

    test("findHistory returns snapshots ordered newest first") {
        val base = Instant.parse("2026-03-24T08:00:00Z")
        repeat(5) { i ->
            repository.save(
                HierarchyRiskSnapshot(
                    snapshotAt = base.plusSeconds(i * 3600L),
                    level = HierarchyLevel.BOOK,
                    entityId = "book-a",
                    parentId = "desk-rates-govts",
                    varValue = 100_000.0 + i * 10_000.0,
                    expectedShortfall = null,
                    pnlToday = null,
                    limitUtilisation = null,
                    marginalVar = null,
                    topContributors = emptyList(),
                )
            )
        }

        val history = repository.findHistory(HierarchyLevel.BOOK, "book-a", limit = 3)

        history shouldHaveSize 3
        history[0].varValue.shouldBeWithinPercentageOf(140_000.0, 0.01)
        history[1].varValue.shouldBeWithinPercentageOf(130_000.0, 0.01)
        history[2].varValue.shouldBeWithinPercentageOf(120_000.0, 0.01)
    }

    test("saves partial snapshot with warning flag") {
        val partial = HierarchyRiskSnapshot(
            snapshotAt = Instant.now(),
            level = HierarchyLevel.FIRM,
            entityId = "FIRM",
            parentId = null,
            varValue = 3_800_000.0,
            expectedShortfall = null,
            pnlToday = null,
            limitUtilisation = null,
            marginalVar = null,
            topContributors = emptyList(),
            isPartial = true,
        )

        repository.save(partial)

        val retrieved = repository.findLatest(HierarchyLevel.FIRM, "FIRM")

        retrieved shouldNotBe null
        retrieved!!.isPartial shouldBe true
    }

    test("returns null when no snapshot exists for entity") {
        val result = repository.findLatest(HierarchyLevel.DIVISION, "nonexistent-div")
        result shouldBe null
    }
})
