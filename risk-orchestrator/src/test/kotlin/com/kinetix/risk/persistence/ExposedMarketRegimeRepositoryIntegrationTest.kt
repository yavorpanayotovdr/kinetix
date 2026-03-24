package com.kinetix.risk.persistence

import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private fun crisisState(detectedAt: Instant = Instant.parse("2026-03-24T14:30:00Z")) = RegimeState(
    regime = MarketRegime.CRISIS,
    detectedAt = detectedAt,
    confidence = 0.85,
    signals = RegimeSignals(
        realisedVol20d = 0.30,
        crossAssetCorrelation = 0.82,
        creditSpreadBps = 245.0,
        pnlVolatility = 0.08,
    ),
    varParameters = AdaptiveVaRParameters(
        calculationType = CalculationType.MONTE_CARLO,
        confidenceLevel = ConfidenceLevel.CL_99,
        timeHorizonDays = 5,
        correlationMethod = "stressed",
        numSimulations = 50_000,
    ),
    consecutiveObservations = 3,
    isConfirmed = true,
    degradedInputs = false,
)

private fun normalState(detectedAt: Instant = Instant.parse("2026-03-24T10:00:00Z")) = RegimeState(
    regime = MarketRegime.NORMAL,
    detectedAt = detectedAt,
    confidence = 0.92,
    signals = RegimeSignals(
        realisedVol20d = 0.10,
        crossAssetCorrelation = 0.40,
        creditSpreadBps = null,
        pnlVolatility = null,
    ),
    varParameters = AdaptiveVaRParameters(
        calculationType = CalculationType.PARAMETRIC,
        confidenceLevel = ConfidenceLevel.CL_95,
        timeHorizonDays = 1,
        correlationMethod = "standard",
        numSimulations = null,
    ),
    consecutiveObservations = 0,
    isConfirmed = true,
    degradedInputs = true,
)

class ExposedMarketRegimeRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: MarketRegimeRepository = ExposedMarketRegimeRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) { MarketRegimeHistoryTable.deleteAll() }
    }

    test("inserts a new regime state and retrieves it as current") {
        val id = repository.insert(crisisState())

        val current = repository.findCurrent()
        current.shouldNotBeNull()
        current.regime shouldBe MarketRegime.CRISIS
        current.id shouldBe id
        current.endedAt.shouldBeNull()
    }

    test("stores all signal fields correctly") {
        repository.insert(crisisState())

        val current = repository.findCurrent()!!
        current.signals.realisedVol20d shouldBe 0.30
        current.signals.crossAssetCorrelation shouldBe 0.82
        current.signals.creditSpreadBps shouldBe 245.0
        current.signals.pnlVolatility shouldBe 0.08
    }

    test("stores VaR parameters correctly for CRISIS regime") {
        repository.insert(crisisState())

        val current = repository.findCurrent()!!
        current.varParameters.calculationType shouldBe CalculationType.MONTE_CARLO
        current.varParameters.confidenceLevel shouldBe ConfidenceLevel.CL_99
        current.varParameters.timeHorizonDays shouldBe 5
        current.varParameters.correlationMethod shouldBe "stressed"
        current.varParameters.numSimulations shouldBe 50_000
    }

    test("stores null optional signal fields for degraded inputs") {
        repository.insert(normalState())

        val current = repository.findCurrent()!!
        current.signals.creditSpreadBps.shouldBeNull()
        current.signals.pnlVolatility.shouldBeNull()
        current.degradedInputs shouldBe true
    }

    test("close sets ended_at on the record") {
        val id = repository.insert(crisisState())
        val endedAt = Instant.parse("2026-03-24T16:00:00Z")
        repository.close(id, endedAt)

        // findCurrent should now return null (the record is closed)
        val current = repository.findCurrent()
        current.shouldBeNull()
    }

    test("findRecent returns all records ordered by startedAt descending") {
        val t1 = Instant.parse("2026-03-24T08:00:00Z")
        val t2 = Instant.parse("2026-03-24T12:00:00Z")
        val t3 = Instant.parse("2026-03-24T16:00:00Z")

        val id1 = repository.insert(normalState(detectedAt = t1))
        val id2 = repository.insert(crisisState(detectedAt = t2))
        repository.close(id2, t3)
        repository.insert(normalState(detectedAt = t3))

        val recent = repository.findRecent(limit = 10)
        recent shouldHaveSize 3
        recent[0].startedAt shouldBe t3
        recent[1].startedAt shouldBe t2
        recent[2].startedAt shouldBe t1
    }

    test("findRecent respects limit parameter") {
        repeat(5) { i ->
            repository.insert(normalState(detectedAt = Instant.parse("2026-03-2${i + 1}T10:00:00Z")))
        }

        val recent = repository.findRecent(limit = 3)
        recent shouldHaveSize 3
    }
})
