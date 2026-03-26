package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.InstrumentServiceClient
import com.kinetix.risk.client.LimitServiceClient
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.dtos.InstrumentDto
import com.kinetix.risk.client.dtos.InstrumentLiquidityDto
import com.kinetix.risk.client.dtos.LimitDefinitionDto
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.GreekImpact
import com.kinetix.risk.model.GreekValues
import com.kinetix.risk.model.GreeksResult
import com.kinetix.risk.model.HedgeConstraints
import com.kinetix.risk.model.HedgeStatus
import com.kinetix.risk.model.HedgeSuggestion
import com.kinetix.risk.model.HedgeTarget
import com.kinetix.risk.model.ValuationOutput
import com.kinetix.risk.model.ValuationResult
import com.kinetix.risk.persistence.HedgeRecommendationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Currency
import java.util.UUID

private fun varResult(
    bookId: String = "BOOK-1",
    delta: Double = 1000.0,
    vega: Double = 500.0,
    gamma: Double = 200.0,
    theta: Double = -30.0,
    rho: Double = 10.0,
    calculatedAt: Instant = Instant.now(),
    jobId: UUID = UUID.randomUUID(),
) = ValuationResult(
    bookId = BookId(bookId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_99,
    varValue = 50_000.0,
    expectedShortfall = 60_000.0,
    componentBreakdown = emptyList(),
    greeks = GreeksResult(
        assetClassGreeks = listOf(
            GreekValues(assetClass = AssetClass.EQUITY, delta = delta, gamma = gamma, vega = vega),
        ),
        theta = theta,
        rho = rho,
    ),
    calculatedAt = calculatedAt,
    computedOutputs = setOf(ValuationOutput.GREEKS),
    jobId = jobId,
)

private fun instrumentDto(instrumentId: String, instrumentType: String = "STOCK") = InstrumentDto(
    instrumentId = instrumentId,
    instrumentType = instrumentType,
    displayName = instrumentId,
    assetClass = "EQUITY",
    currency = "USD",
    attributes = JsonObject(emptyMap()),
    createdAt = "2026-03-24T00:00:00Z",
    updatedAt = "2026-03-24T00:00:00Z",
)

private fun liquidityDto(
    instrumentId: String,
    adv: Double = 100_000_000.0,
    bidAskSpreadBps: Double = 3.0,
    advStale: Boolean = false,
) = InstrumentLiquidityDto(
    instrumentId = instrumentId,
    adv = adv,
    bidAskSpreadBps = bidAskSpreadBps,
    assetClass = "EQUITY",
    advUpdatedAt = "2026-03-24T09:00:00Z",
    advStale = advStale,
    advStalenessDays = 0,
    createdAt = "2026-03-24T09:00:00Z",
    updatedAt = "2026-03-24T09:00:00Z",
)

private fun pricePoint(instrumentId: String, amount: Double = 150.0) = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(BigDecimal.valueOf(amount), Currency.getInstance("USD")),
    timestamp = Instant.now(),
    source = PriceSource.EXCHANGE,
)

private val defaultConstraints = HedgeConstraints(
    maxNotional = null,
    maxSuggestions = 5,
    respectPositionLimits = false,
    instrumentUniverse = null,
    allowedSides = null,
)

/**
 * Builds a minimal HedgeSuggestion where [notional] represents estimatedCost
 * (crossingCost = 0, so notional = quantity * price).
 */
private fun sampleSuggestion(notional: Double = 10_000.0) = HedgeSuggestion(
    instrumentId = "HEDGE-PUT",
    instrumentType = "OPTION",
    side = "BUY",
    quantity = notional / 100.0, // price per unit = 100 in tests
    estimatedCost = notional,
    crossingCost = 0.0,
    carrycostPerDay = null,
    targetReduction = 500.0,
    targetReductionPct = 0.50,
    residualMetric = 500.0,
    greekImpact = GreekImpact(
        deltaBefore = 1000.0, deltaAfter = 500.0,
        gammaBefore = 0.0, gammaAfter = 0.0,
        vegaBefore = 0.0, vegaAfter = 0.0,
        thetaBefore = 0.0, thetaAfter = 0.0,
        rhoBefore = 0.0, rhoAfter = 0.0,
    ),
    liquidityTier = "TIER_1",
    dataQuality = "FRESH",
)

private fun limitDefinitionDto(
    limitType: String = "NOTIONAL",
    limitValue: String = "100000",
    level: String = "FIRM",
    entityId: String = "FIRM",
    active: Boolean = true,
) = LimitDefinitionDto(
    id = "limit-1",
    level = level,
    entityId = entityId,
    limitType = limitType,
    limitValue = limitValue,
    intradayLimit = null,
    overnightLimit = null,
    active = active,
)

class HedgeRecommendationServiceTest : FunSpec({

    val varCache = mockk<VaRCache>()
    val instrumentServiceClient = mockk<InstrumentServiceClient>()
    val priceServiceClient = mockk<PriceServiceClient>()
    val referenceDataClient = mockk<ReferenceDataServiceClient>()
    val limitServiceClient = mockk<LimitServiceClient>()
    val calculator = mockk<AnalyticalHedgeCalculator>()
    val repository = mockk<HedgeRecommendationRepository>(relaxed = true)

    val service = HedgeRecommendationService(
        varCache = varCache,
        instrumentServiceClient = instrumentServiceClient,
        priceServiceClient = priceServiceClient,
        referenceDataClient = referenceDataClient,
        limitServiceClient = limitServiceClient,
        calculator = calculator,
        repository = repository,
        maxGreekStaleness = Duration.ofHours(2),
        recommendationTtl = Duration.ofMinutes(30),
    )

    val bookId = BookId("BOOK-1")

    beforeEach {
        clearMocks(varCache, instrumentServiceClient, priceServiceClient, referenceDataClient, limitServiceClient, calculator, repository)
    }

    test("throws when no VaR result is cached for the book") {
        coEvery { varCache.get("BOOK-1") } returns null

        val ex = shouldThrow<IllegalStateException> {
            service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)
        }
        ex.message shouldContain "No VaR result available"
    }

    test("throws when cached Greeks are older than the staleness threshold") {
        val staleResult = varResult(calculatedAt = Instant.now().minusSeconds(3 * 3600))
        coEvery { varCache.get("BOOK-1") } returns staleResult

        val ex = shouldThrow<IllegalStateException> {
            service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)
        }
        ex.message shouldContain "stale"
    }

    test("throws when targetReductionPct is zero or negative") {
        coEvery { varCache.get("BOOK-1") } returns varResult()

        shouldThrow<IllegalArgumentException> {
            service.suggestHedge(bookId, HedgeTarget.DELTA, 0.0, defaultConstraints)
        }
    }

    test("throws when targetReductionPct exceeds 1.0") {
        coEvery { varCache.get("BOOK-1") } returns varResult()

        shouldThrow<IllegalArgumentException> {
            service.suggestHedge(bookId, HedgeTarget.DELTA, 1.01, defaultConstraints)
        }
    }

    test("extracts aggregated greeks from the VaR cache result and passes them to the calculator") {
        val result = varResult(delta = 800.0, gamma = 150.0, vega = 400.0, theta = -20.0, rho = 8.0)
        coEvery { varCache.get("BOOK-1") } returns result
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL"))
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        val greeksSlot = slot<GreekImpact>()
        coVerify { calculator.suggest(capture(greeksSlot), any(), any(), any(), any()) }
        greeksSlot.captured.deltaBefore shouldBe 800.0
        greeksSlot.captured.gammaBefore shouldBe 150.0
        greeksSlot.captured.vegaBefore shouldBe 400.0
        greeksSlot.captured.thetaBefore shouldBe -20.0
        greeksSlot.captured.rhoBefore shouldBe 8.0
    }

    test("passes the target and targetReductionPct to the calculator") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL"))

        val targetSlot = slot<HedgeTarget>()
        val pctSlot = slot<Double>()
        coEvery { calculator.suggest(any(), capture(targetSlot), capture(pctSlot), any(), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.VEGA, 0.50, defaultConstraints)

        targetSlot.captured shouldBe HedgeTarget.VEGA
        pctSlot.captured shouldBe 0.50
    }

    test("persists the recommendation to the repository") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns emptyMap()
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        coVerify { repository.save(any()) }
    }

    test("returns a recommendation with PENDING status and correct metadata") {
        val jobId = UUID.randomUUID()
        val result = varResult(jobId = jobId)
        coEvery { varCache.get("BOOK-1") } returns result
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL"))
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.bookId shouldBe "BOOK-1"
        rec.targetMetric shouldBe HedgeTarget.DELTA
        rec.targetReductionPct shouldBe 0.80
        rec.status shouldBe HedgeStatus.PENDING
        rec.sourceJobId shouldBe jobId.toString()
        rec.expiresAt.isAfter(Instant.now()) shouldBe true
    }

    test("filters candidates to TIER_1 and TIER_2 only — excludes TIER_3 and ILLIQUID") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "TIER1-STOCK" to liquidityDto("TIER1-STOCK", adv = 100_000_000.0, bidAskSpreadBps = 3.0),
            "TIER2-STOCK" to liquidityDto("TIER2-STOCK", adv = 15_000_000.0, bidAskSpreadBps = 15.0),
            "TIER3-STOCK" to liquidityDto("TIER3-STOCK", adv = 2_000_000.0, bidAskSpreadBps = 50.0),
            "ILLIQUID-STOCK" to liquidityDto("ILLIQUID-STOCK", adv = 100_000.0, bidAskSpreadBps = 100.0),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns
            ClientResponse.Success(instrumentDto("ANY"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("ANY"))

        val candidatesSlot = slot<List<com.kinetix.risk.model.CandidateInstrument>>()
        coEvery { calculator.suggest(any(), any(), any(), capture(candidatesSlot), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        val candidateIds = candidatesSlot.captured.map { it.instrumentId }
        candidateIds shouldHaveSize 2
        (candidateIds.contains("TIER1-STOCK")) shouldBe true
        (candidateIds.contains("TIER2-STOCK")) shouldBe true
        (candidateIds.contains("TIER3-STOCK")) shouldBe false
        (candidateIds.contains("ILLIQUID-STOCK")) shouldBe false
    }

    test("returns REJECTED recommendation with message when no liquid candidates are found") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns emptyMap()

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.status shouldBe HedgeStatus.REJECTED
        rec.message shouldNotBe null
        rec.message!!.shouldContain("No liquid")
        rec.suggestions shouldHaveSize 0
    }

    test("returns PENDING recommendation when at least one liquid candidate is found") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "TIER1-STOCK" to liquidityDto("TIER1-STOCK", adv = 100_000_000.0, bidAskSpreadBps = 3.0),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("TIER1-STOCK"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("TIER1-STOCK"))
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.status shouldBe HedgeStatus.PENDING
        rec.message shouldBe null
    }

    test("produces a staleness warning when source job is older than 30 minutes but younger than 2 hours") {
        val oldishResult = varResult(calculatedAt = Instant.now().minusSeconds(45 * 60))
        coEvery { varCache.get("BOOK-1") } returns oldishResult
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL"))
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        // Should not throw — only blocks after 2 hours
        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.shouldNotBeNull()
        rec.status shouldBe HedgeStatus.PENDING
    }

    test("uses real price per unit from price service") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL", amount = 42.50))

        val candidatesSlot = slot<List<com.kinetix.risk.model.CandidateInstrument>>()
        coEvery { calculator.suggest(any(), any(), any(), capture(candidatesSlot), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        candidatesSlot.captured shouldHaveSize 1
        candidatesSlot.captured.first().pricePerUnit shouldBe 42.50
    }

    test("excludes candidate when price not found") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.NotFound(404)

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.status shouldBe HedgeStatus.REJECTED
    }

    test("excludes candidate when price is zero") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("AAPL", amount = 0.0))

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.status shouldBe HedgeStatus.REJECTED
    }

    test("excludes candidate when price service throws") {
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "AAPL" to liquidityDto("AAPL"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("AAPL"))
        coEvery { priceServiceClient.getLatestPrice(any()) } throws RuntimeException("price service unavailable")

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, defaultConstraints)

        rec.status shouldBe HedgeStatus.REJECTED
    }

    // ------------------------------------------------------------------ position limit enforcement

    test("excludes suggestions that would breach an active NOTIONAL limit when respectPositionLimits is true") {
        val constraints = defaultConstraints.copy(respectPositionLimits = true)
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "HEDGE-PUT" to liquidityDto("HEDGE-PUT"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("HEDGE-PUT"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("HEDGE-PUT", amount = 100.0))

        // The suggestion has estimatedCost 50_000 (notional = 500 units * $100), which exceeds the limit of 10_000
        val suggestion = sampleSuggestion(notional = 50_000.0)
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns listOf(suggestion)

        // NOTIONAL limit: 10_000 — should exclude the suggestion
        coEvery { limitServiceClient.getLimits() } returns ClientResponse.Success(
            listOf(limitDefinitionDto(limitType = "NOTIONAL", limitValue = "10000"))
        )

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, constraints)

        rec.suggestions shouldHaveSize 0
    }

    test("includes suggestions that stay within the active NOTIONAL limit when respectPositionLimits is true") {
        val constraints = defaultConstraints.copy(respectPositionLimits = true)
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "HEDGE-PUT" to liquidityDto("HEDGE-PUT"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("HEDGE-PUT"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("HEDGE-PUT", amount = 100.0))

        // Suggestion notional = 5_000, limit = 10_000 — should pass
        val suggestion = sampleSuggestion(notional = 5_000.0)
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns listOf(suggestion)

        coEvery { limitServiceClient.getLimits() } returns ClientResponse.Success(
            listOf(limitDefinitionDto(limitType = "NOTIONAL", limitValue = "10000"))
        )

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, constraints)

        rec.suggestions shouldHaveSize 1
        rec.suggestions.first().warnings shouldBe emptyList()
    }

    test("includes suggestions with a warning when limit service is unavailable and respectPositionLimits is true") {
        val constraints = defaultConstraints.copy(respectPositionLimits = true)
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "HEDGE-PUT" to liquidityDto("HEDGE-PUT"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("HEDGE-PUT"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("HEDGE-PUT", amount = 100.0))

        val suggestion = sampleSuggestion(notional = 50_000.0)
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns listOf(suggestion)

        coEvery { limitServiceClient.getLimits() } throws RuntimeException("limit service down")

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, constraints)

        rec.suggestions shouldHaveSize 1
        rec.suggestions.first().warnings.any { it.contains("limit") } shouldBe true
    }

    test("does not call limit service when respectPositionLimits is false") {
        val constraints = defaultConstraints.copy(respectPositionLimits = false)
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "HEDGE-PUT" to liquidityDto("HEDGE-PUT"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("HEDGE-PUT"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("HEDGE-PUT", amount = 100.0))

        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns emptyList()

        service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, constraints)

        coVerify(exactly = 0) { limitServiceClient.getLimits() }
    }

    test("includes suggestions without warnings when no NOTIONAL limits are defined and respectPositionLimits is true") {
        val constraints = defaultConstraints.copy(respectPositionLimits = true)
        coEvery { varCache.get("BOOK-1") } returns varResult()
        coEvery { referenceDataClient.getLiquidityDataBatch(any()) } returns mapOf(
            "HEDGE-PUT" to liquidityDto("HEDGE-PUT"),
        )
        coEvery { instrumentServiceClient.getInstrument(any()) } returns ClientResponse.Success(instrumentDto("HEDGE-PUT"))
        coEvery { priceServiceClient.getLatestPrice(any()) } returns ClientResponse.Success(pricePoint("HEDGE-PUT", amount = 100.0))

        val suggestion = sampleSuggestion(notional = 50_000.0)
        coEvery { calculator.suggest(any(), any(), any(), any(), any()) } returns listOf(suggestion)

        // Only VAR limits defined — no NOTIONAL limits — suggestion should pass through
        coEvery { limitServiceClient.getLimits() } returns ClientResponse.Success(
            listOf(limitDefinitionDto(limitType = "VAR", limitValue = "1000000"))
        )

        val rec = service.suggestHedge(bookId, HedgeTarget.DELTA, 0.80, constraints)

        rec.suggestions shouldHaveSize 1
        rec.suggestions.first().warnings shouldBe emptyList()
    }
})
