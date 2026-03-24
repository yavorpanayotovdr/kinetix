package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.HierarchyDataClient
import com.kinetix.risk.model.BookHierarchyEntry
import com.kinetix.risk.model.BookVaRContribution
import com.kinetix.risk.model.BreachStatus
import com.kinetix.risk.model.BudgetUtilisation
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.CrossBookValuationResult
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.ValuationResult
import com.kinetix.risk.persistence.RiskHierarchySnapshotRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class HierarchyRiskServiceTest : FunSpec({

    val hierarchyDataClient = mockk<HierarchyDataClient>()
    val crossBookVaRService = mockk<CrossBookVaRCalculationService>()
    val snapshotRepository = mockk<RiskHierarchySnapshotRepository>(relaxed = true)
    val varCache = mockk<VaRCache>()
    val budgetUtilisationService = mockk<BudgetUtilisationService>()

    val service = HierarchyRiskService(
        hierarchyDataClient = hierarchyDataClient,
        crossBookVaRService = crossBookVaRService,
        snapshotRepository = snapshotRepository,
        varCache = varCache,
        budgetUtilisationService = budgetUtilisationService,
    )

    beforeEach {
        clearMocks(hierarchyDataClient, crossBookVaRService, varCache, snapshotRepository, budgetUtilisationService)
        // default: all books have no VaR cached
        every { varCache.get(any()) } returns null
        // default: no budget configured
        coEvery { budgetUtilisationService.computeUtilisation(any(), any(), any()) } returns null
    }

    // ── FIRM level ────────────────────────────────────────────────────────────

    test("aggregates VaR across all books for firm-level request") {
        val divA = Division(DivisionId("div-a"), "Rates Division")
        val divB = Division(DivisionId("div-b"), "Equities Division")
        val deskRates = Desk(DeskId("desk-rates"), "Rates Desk", DivisionId("div-a"))
        val deskEquities = Desk(DeskId("desk-equities"), "Equities Desk", DivisionId("div-b"))
        val bookMappings = listOf(
            BookHierarchyEntry("book-r1", "desk-rates", "Rates Book 1", null),
            BookHierarchyEntry("book-e1", "desk-equities", "Equities Book 1", null),
        )

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA, divB)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(deskRates, deskEquities)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), varValue = 1_000_000.0)
        every { varCache.get("book-e1") } returns stubResult(BookId("book-e1"), varValue = 800_000.0)

        val crossBookResult = stubCrossBookResult(
            bookIds = listOf(BookId("book-r1"), BookId("book-e1")),
            aggregateVar = 1_600_000.0,
            contributions = listOf(
                stubContribution("book-r1", 900_000.0),
                stubContribution("book-e1", 700_000.0),
            ),
        )
        coEvery { crossBookVaRService.calculate(any(), any()) } returns crossBookResult

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")

        result shouldNotBe null
        result!!.level shouldBe HierarchyLevel.FIRM
        result.entityId shouldBe "FIRM"
        result.varValue.shouldBeWithinPercentageOf(1_600_000.0, 0.01)
        result.isPartial shouldBe false
    }

    test("firm VaR is less than sum of standalone VaRs — diversification benefit exists") {
        val divA = Division(DivisionId("div-a"), "Rates Division")
        val desk = Desk(DeskId("desk-rates"), "Rates Desk", DivisionId("div-a"))
        val bookMappings = listOf(
            BookHierarchyEntry("book-r1", "desk-rates", null, null),
            BookHierarchyEntry("book-r2", "desk-rates", null, null),
        )

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), varValue = 500_000.0)
        every { varCache.get("book-r2") } returns stubResult(BookId("book-r2"), varValue = 500_000.0)

        // Aggregate VaR = 800k, standalone sum = 1M — 200k diversification benefit
        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-r1"), BookId("book-r2")),
                800_000.0,
                listOf(stubContribution("book-r1", 400_000.0), stubContribution("book-r2", 400_000.0)),
            )

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")!!

        result.varValue shouldBe 800_000.0
        // diversified VaR < sum of standalone VaRs
        val standaloneSum = 500_000.0 + 500_000.0
        (result.varValue < standaloneSum) shouldBe true
    }

    // ── Top contributors ──────────────────────────────────────────────────────

    test("returns top 5 contributors sorted by absolute var contribution descending") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "Rates Desk", DivisionId("div-a"))
        val bookMappings = (1..7).map { i ->
            BookHierarchyEntry("book-$i", "d1", "Book $i", null)
        }

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        (1..7).forEach { i ->
            every { varCache.get("book-$i") } returns stubResult(BookId("book-$i"), i * 100_000.0)
        }

        val bookIds = (1..7).map { BookId("book-$it") }
        val contributions = (1..7).map { i -> stubContribution("book-$i", i * 90_000.0) }
        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(bookIds, 2_000_000.0, contributions)

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")!!

        result.topContributors shouldHaveSize 5
        // First contributor is "book-7" (highest contribution = 630k)
        result.topContributors[0].entityId shouldBe "book-7"
        result.topContributors[1].entityId shouldBe "book-6"
    }

    // ── Partial aggregation on failure ────────────────────────────────────────

    test("produces partial result when one book has no cached VaR") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "Rates Desk", DivisionId("div-a"))
        val bookMappings = listOf(
            BookHierarchyEntry("book-ok", "d1", null, null),
            BookHierarchyEntry("book-missing", "d1", null, null),
        )

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-ok") } returns stubResult(BookId("book-ok"), 500_000.0)
        // book-missing has no cache entry (default mock returns null)

        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-ok")),
                500_000.0,
                listOf(stubContribution("book-ok", 500_000.0)),
            )

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")!!

        result.isPartial shouldBe true
        result.missingBooks shouldBe listOf("book-missing")
    }

    // ── Desk level ────────────────────────────────────────────────────────────

    test("aggregates VaR for a single desk using only books in that desk") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("desk-rates"), "Rates Desk", DivisionId("div-a"))
        val bookMappings = listOf(
            BookHierarchyEntry("book-r1", "desk-rates", "Book 1", null),
            BookHierarchyEntry("book-r2", "desk-rates", "Book 2", null),
            BookHierarchyEntry("book-e1", "desk-equities", "Equities Book", null),
        )

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 600_000.0)
        every { varCache.get("book-r2") } returns stubResult(BookId("book-r2"), 400_000.0)

        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-r1"), BookId("book-r2")),
                900_000.0,
                listOf(stubContribution("book-r1", 500_000.0), stubContribution("book-r2", 400_000.0)),
            )

        val result = service.aggregateHierarchy(HierarchyLevel.DESK, "desk-rates")!!

        result.level shouldBe HierarchyLevel.DESK
        result.entityId shouldBe "desk-rates"
        result.varValue.shouldBeWithinPercentageOf(900_000.0, 0.01)
    }

    // ── Empty desk ────────────────────────────────────────────────────────────

    test("returns zero VaR for a desk with no books — no exception") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("empty-desk"), "Empty Desk", DivisionId("div-a"))

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns emptyList()

        val result = service.aggregateHierarchy(HierarchyLevel.DESK, "empty-desk")!!

        result.varValue shouldBe 0.0
        result.childCount shouldBe 0
        result.isPartial shouldBe false
    }

    // ── Budget utilisation ────────────────────────────────────────────────────

    test("populates limitUtilisation when a budget is configured for the entity") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "D1", DivisionId("div-a"))

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns
            listOf(BookHierarchyEntry("book-r1", "d1", null, null))
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 2_000_000.0)

        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-r1")),
                2_000_000.0,
                listOf(stubContribution("book-r1", 2_000_000.0)),
            )

        val utilisation = BudgetUtilisation(
            entityLevel = HierarchyLevel.FIRM,
            entityId = "FIRM",
            budgetType = "VAR_BUDGET",
            budgetAmount = BigDecimal("5000000.00"),
            currentVar = BigDecimal("2000000.00"),
            utilisationPct = BigDecimal("40.00"),
            breachStatus = BreachStatus.WITHIN_BUDGET,
            updatedAt = Instant.now(),
        )
        coEvery {
            budgetUtilisationService.computeUtilisation(HierarchyLevel.FIRM, "FIRM", any())
        } returns utilisation

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")!!

        result.limitUtilisation shouldBe 40.0
    }

    test("leaves limitUtilisation null when no budget is configured") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "D1", DivisionId("div-a"))

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns
            listOf(BookHierarchyEntry("book-r1", "d1", null, null))
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 1_000_000.0)

        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-r1")),
                1_000_000.0,
                listOf(stubContribution("book-r1", 1_000_000.0)),
            )
        // budgetUtilisationService returns null (default stub)

        val result = service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")!!

        result.limitUtilisation shouldBe null
    }

    // ── Marginal and incremental VaR ─────────────────────────────────────────

    test("populates marginalVar as the sum of book marginal VaR contributions") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "D1", DivisionId("div-a"))
        val bookMappings = listOf(
            BookHierarchyEntry("book-r1", "d1", null, null),
            BookHierarchyEntry("book-r2", "d1", null, null),
        )

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 500_000.0)
        every { varCache.get("book-r2") } returns stubResult(BookId("book-r2"), 500_000.0)

        val contributions = listOf(
            BookVaRContribution(BookId("book-r1"), 400_000.0, 50.0, 500_000.0, 100_000.0, marginalVar = 350_000.0, incrementalVar = 380_000.0),
            BookVaRContribution(BookId("book-r2"), 400_000.0, 50.0, 500_000.0, 100_000.0, marginalVar = 300_000.0, incrementalVar = 340_000.0),
        )
        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(listOf(BookId("book-r1"), BookId("book-r2")), 800_000.0, contributions)

        val result = service.aggregateHierarchy(HierarchyLevel.DESK, "d1")!!

        result.marginalVar shouldBe 650_000.0   // 350k + 300k
        result.incrementalVar shouldBe 720_000.0 // 380k + 340k
    }

    test("marginalVar is null when cross-book result is null") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "D1", DivisionId("div-a"))
        val bookMappings = listOf(BookHierarchyEntry("book-r1", "d1", null, null))

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns bookMappings
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 500_000.0)

        coEvery { crossBookVaRService.calculate(any(), any()) } throws RuntimeException("gRPC failure")

        val result = service.aggregateHierarchy(HierarchyLevel.DESK, "d1")!!

        result.marginalVar shouldBe null
        result.incrementalVar shouldBe null
    }

    // ── Snapshot persistence ──────────────────────────────────────────────────

    test("persists a HierarchyRiskSnapshot after successful aggregation") {
        val divA = Division(DivisionId("div-a"), "Rates")
        val desk = Desk(DeskId("d1"), "D1", DivisionId("div-a"))

        coEvery { hierarchyDataClient.getAllDivisions() } returns listOf(divA)
        coEvery { hierarchyDataClient.getAllDesks() } returns listOf(desk)
        coEvery { hierarchyDataClient.getAllBookMappings() } returns
            listOf(BookHierarchyEntry("book-r1", "d1", null, null))
        every { varCache.get("book-r1") } returns stubResult(BookId("book-r1"), 300_000.0)

        coEvery { crossBookVaRService.calculate(any(), any()) } returns
            stubCrossBookResult(
                listOf(BookId("book-r1")),
                300_000.0,
                listOf(stubContribution("book-r1", 300_000.0)),
            )

        service.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")

        coVerify(exactly = 1) { snapshotRepository.save(any()) }
    }
})

// ── Stub helpers ──────────────────────────────────────────────────────────────

private fun stubResult(bookId: BookId, varValue: Double): ValuationResult =
    ValuationResult(
        bookId = bookId,
        calculationType = com.kinetix.risk.model.CalculationType.PARAMETRIC,
        confidenceLevel = com.kinetix.risk.model.ConfidenceLevel.CL_95,
        varValue = varValue,
        expectedShortfall = null,
        componentBreakdown = emptyList(),
        greeks = null,
        calculatedAt = Instant.now(),
        computedOutputs = setOf(com.kinetix.risk.model.ValuationOutput.VAR),
    )

private fun stubContribution(bookId: String, varContribution: Double): BookVaRContribution =
    BookVaRContribution(
        bookId = BookId(bookId),
        varContribution = varContribution,
        percentageOfTotal = 0.0,
        standaloneVar = varContribution,
        diversificationBenefit = 0.0,
    )

private fun stubCrossBookResult(
    bookIds: List<BookId>,
    aggregateVar: Double,
    contributions: List<BookVaRContribution>,
): CrossBookValuationResult = CrossBookValuationResult(
    portfolioGroupId = "test-group",
    bookIds = bookIds,
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = aggregateVar,
    expectedShortfall = aggregateVar * 1.4,
    componentBreakdown = emptyList(),
    bookContributions = contributions,
    totalStandaloneVar = contributions.sumOf { it.standaloneVar },
    diversificationBenefit = contributions.sumOf { it.standaloneVar } - aggregateVar,
    calculatedAt = Instant.now(),
    modelVersion = "test",
    monteCarloSeed = 0L,
    jobId = UUID.randomUUID(),
)
