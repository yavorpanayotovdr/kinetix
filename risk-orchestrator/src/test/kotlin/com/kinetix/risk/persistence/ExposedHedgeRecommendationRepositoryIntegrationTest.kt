package com.kinetix.risk.persistence

import com.kinetix.risk.model.GreekImpact
import com.kinetix.risk.model.HedgeConstraints
import com.kinetix.risk.model.HedgeRecommendation
import com.kinetix.risk.model.HedgeStatus
import com.kinetix.risk.model.HedgeSuggestion
import com.kinetix.risk.model.HedgeTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

class ExposedHedgeRecommendationRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: HedgeRecommendationRepository = ExposedHedgeRecommendationRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { HedgeRecommendationsTable.deleteAll() }
    }

    fun sampleGreekImpact() = GreekImpact(
        deltaBefore = 1000.0, deltaAfter = 50.0,
        gammaBefore = 200.0, gammaAfter = 180.0,
        vegaBefore = 500.0, vegaAfter = 480.0,
        thetaBefore = -30.0, thetaAfter = -31.5,
        rhoBefore = 10.0, rhoAfter = 9.8,
    )

    fun sampleSuggestion() = HedgeSuggestion(
        instrumentId = "AAPL-P-2026",
        instrumentType = "OPTION",
        side = "BUY",
        quantity = 10.0,
        estimatedCost = 12_000.0,
        crossingCost = 250.0,
        carrycostPerDay = -5.0,
        targetReduction = 950.0,
        targetReductionPct = 0.95,
        residualMetric = 50.0,
        greekImpact = sampleGreekImpact(),
        liquidityTier = "TIER_1",
        dataQuality = "FRESH",
    )

    fun sampleRecommendation(
        bookId: String = "BOOK-1",
        status: HedgeStatus = HedgeStatus.PENDING,
    ) = HedgeRecommendation(
        id = UUID.randomUUID(),
        bookId = bookId,
        targetMetric = HedgeTarget.DELTA,
        targetReductionPct = 0.90,
        requestedAt = Instant.parse("2026-03-24T10:00:00Z"),
        status = status,
        constraints = HedgeConstraints(
            maxNotional = 500_000.0,
            maxSuggestions = 5,
            respectPositionLimits = true,
            instrumentUniverse = null,
            allowedSides = listOf("BUY"),
        ),
        suggestions = listOf(sampleSuggestion()),
        preHedgeGreeks = sampleGreekImpact(),
        sourceJobId = "job-abc-123",
        acceptedBy = null,
        acceptedAt = null,
        expiresAt = Instant.parse("2026-03-24T10:30:00Z"),
    )

    test("saves and retrieves a hedge recommendation by id") {
        val rec = sampleRecommendation()
        repository.save(rec)

        val found = repository.findById(rec.id)

        found.shouldNotBeNull()
        found.id shouldBe rec.id
        found.bookId shouldBe "BOOK-1"
        found.targetMetric shouldBe HedgeTarget.DELTA
        found.targetReductionPct.shouldBeWithinPercentageOf(0.90, 0.01)
        found.status shouldBe HedgeStatus.PENDING
        found.sourceJobId shouldBe "job-abc-123"
    }

    test("returns null when recommendation is not found") {
        val found = repository.findById(UUID.randomUUID())
        found.shouldBeNull()
    }

    test("saves and restores suggestions with full greek impact") {
        val rec = sampleRecommendation()
        repository.save(rec)

        val found = repository.findById(rec.id)!!

        found.suggestions shouldHaveSize 1
        val suggestion = found.suggestions.first()
        suggestion.instrumentId shouldBe "AAPL-P-2026"
        suggestion.side shouldBe "BUY"
        suggestion.estimatedCost.shouldBeWithinPercentageOf(12_000.0, 0.01)
        suggestion.liquidityTier shouldBe "TIER_1"
        suggestion.dataQuality shouldBe "FRESH"
        suggestion.greekImpact.deltaBefore.shouldBeWithinPercentageOf(1000.0, 0.01)
        suggestion.greekImpact.deltaAfter.shouldBeWithinPercentageOf(50.0, 0.01)
        suggestion.greekImpact.vegaBefore.shouldBeWithinPercentageOf(500.0, 0.01)
    }

    test("saves and restores constraints") {
        val rec = sampleRecommendation()
        repository.save(rec)

        val found = repository.findById(rec.id)!!

        found.constraints.maxNotional.shouldNotBeNull()
        found.constraints.maxNotional!!.shouldBeWithinPercentageOf(500_000.0, 0.01)
        found.constraints.maxSuggestions shouldBe 5
        found.constraints.respectPositionLimits shouldBe true
        found.constraints.allowedSides shouldBe listOf("BUY")
    }

    test("findLatestByBookId returns recommendations in descending order") {
        val earlier = sampleRecommendation()
            .copy(id = UUID.randomUUID(), requestedAt = Instant.parse("2026-03-24T08:00:00Z"))
        val later = sampleRecommendation()
            .copy(id = UUID.randomUUID(), requestedAt = Instant.parse("2026-03-24T10:00:00Z"))
        repository.save(earlier)
        repository.save(later)

        val results = repository.findLatestByBookId("BOOK-1")

        results shouldHaveSize 2
        results[0].requestedAt shouldBe Instant.parse("2026-03-24T10:00:00Z")
        results[1].requestedAt shouldBe Instant.parse("2026-03-24T08:00:00Z")
    }

    test("findLatestByBookId returns empty list for unknown book") {
        val results = repository.findLatestByBookId("UNKNOWN")
        results shouldHaveSize 0
    }

    test("updateStatus changes status to ACCEPTED with acceptedBy and acceptedAt") {
        val rec = sampleRecommendation()
        repository.save(rec)

        val now = Instant.parse("2026-03-24T11:00:00Z")
        repository.updateStatus(rec.id, HedgeStatus.ACCEPTED, acceptedBy = "trader@kinetix.com", acceptedAt = now)

        val found = repository.findById(rec.id)!!
        found.status shouldBe HedgeStatus.ACCEPTED
        found.acceptedBy shouldBe "trader@kinetix.com"
        found.acceptedAt shouldBe now
    }

    test("updateStatus changes status to REJECTED") {
        val rec = sampleRecommendation()
        repository.save(rec)

        repository.updateStatus(rec.id, HedgeStatus.REJECTED)

        val found = repository.findById(rec.id)!!
        found.status shouldBe HedgeStatus.REJECTED
    }

    test("expirePending marks all pending recommendations past their expiry time as EXPIRED") {
        val expired = sampleRecommendation()
            .copy(id = UUID.randomUUID(), expiresAt = Instant.now().minusSeconds(60))
        val current = sampleRecommendation()
            .copy(id = UUID.randomUUID(), expiresAt = Instant.now().plusSeconds(1800))
        repository.save(expired)
        repository.save(current)

        val count = repository.expirePending()

        count shouldBe 1
        repository.findById(expired.id)!!.status shouldBe HedgeStatus.EXPIRED
        repository.findById(current.id)!!.status shouldBe HedgeStatus.PENDING
    }
})
