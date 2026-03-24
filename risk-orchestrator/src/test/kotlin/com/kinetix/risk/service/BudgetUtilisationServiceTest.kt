package com.kinetix.risk.service

import com.kinetix.risk.model.BreachStatus
import com.kinetix.risk.model.BudgetPeriod
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.RiskBudgetAllocation
import com.kinetix.risk.persistence.RiskBudgetAllocationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate

class BudgetUtilisationServiceTest : FunSpec({

    val budgetRepository = mockk<RiskBudgetAllocationRepository>()

    val service = BudgetUtilisationService(
        budgetRepository = budgetRepository,
        warningThreshold = 0.80,
        breachThreshold = 1.00,
    )

    beforeEach { clearMocks(budgetRepository) }

    // ── Status determination ──────────────────────────────────────────────────

    test("reports WITHIN_BUDGET when VaR is below the warning threshold") {
        stubBudget(budgetRepository, HierarchyLevel.DESK, "desk-rates", budgetAmount = 5_000_000.0)

        val result = service.computeUtilisation(
            level = HierarchyLevel.DESK,
            entityId = "desk-rates",
            currentVar = BigDecimal("3000000.0"),
        )!!

        result.utilisationPct shouldBe BigDecimal("60.00")
        result.breachStatus shouldBe BreachStatus.WITHIN_BUDGET
    }

    test("reports WARNING when VaR reaches the 80% threshold") {
        stubBudget(budgetRepository, HierarchyLevel.DESK, "desk-rates", budgetAmount = 5_000_000.0)

        val result = service.computeUtilisation(
            level = HierarchyLevel.DESK,
            entityId = "desk-rates",
            currentVar = BigDecimal("4000000.0"),
        )!!

        result.utilisationPct shouldBe BigDecimal("80.00")
        result.breachStatus shouldBe BreachStatus.WARNING
    }

    test("reports BREACH when VaR exceeds the budget amount") {
        stubBudget(budgetRepository, HierarchyLevel.DESK, "desk-rates", budgetAmount = 5_000_000.0)

        val result = service.computeUtilisation(
            level = HierarchyLevel.DESK,
            entityId = "desk-rates",
            currentVar = BigDecimal("5500000.0"),
        )!!

        result.utilisationPct shouldBe BigDecimal("110.00")
        result.breachStatus shouldBe BreachStatus.BREACH
    }

    test("returns null when no active budget is configured for the entity") {
        coEvery {
            budgetRepository.findEffective(HierarchyLevel.DESK, "desk-with-no-budget", "VAR_BUDGET", any())
        } returns null

        val result = service.computeUtilisation(
            level = HierarchyLevel.DESK,
            entityId = "desk-with-no-budget",
            currentVar = BigDecimal("100000.0"),
        )

        result shouldBe null
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    test("reports WITHIN_BUDGET when VaR is exactly at the warning threshold") {
        stubBudget(budgetRepository, HierarchyLevel.FIRM, "FIRM", budgetAmount = 10_000_000.0)

        val result = service.computeUtilisation(
            level = HierarchyLevel.FIRM,
            entityId = "FIRM",
            currentVar = BigDecimal("8000000.0"),
        )!!

        // Exactly 80.00% — should be WARNING (>= threshold, not strictly greater)
        result.breachStatus shouldBe BreachStatus.WARNING
    }

    test("result carries correct budgetAmount and currentVar fields") {
        stubBudget(budgetRepository, HierarchyLevel.DIVISION, "div-equities", budgetAmount = 2_000_000.0)

        val currentVar = BigDecimal("1200000.0")
        val result = service.computeUtilisation(
            level = HierarchyLevel.DIVISION,
            entityId = "div-equities",
            currentVar = currentVar,
        )!!

        result.entityLevel shouldBe HierarchyLevel.DIVISION
        result.entityId shouldBe "div-equities"
        result.budgetType shouldBe "VAR_BUDGET"
        result.currentVar.compareTo(currentVar) shouldBe 0
    }
})

private suspend fun stubBudget(
    repo: RiskBudgetAllocationRepository,
    level: HierarchyLevel,
    entityId: String,
    budgetAmount: Double,
) {
    coEvery {
        repo.findEffective(level, entityId, "VAR_BUDGET", any())
    } returns RiskBudgetAllocation(
        id = "stub-budget",
        entityLevel = level,
        entityId = entityId,
        budgetType = "VAR_BUDGET",
        budgetPeriod = BudgetPeriod.DAILY,
        budgetAmount = BigDecimal(budgetAmount.toString()),
        effectiveFrom = LocalDate.of(2026, 1, 1),
        effectiveTo = null,
        allocatedBy = "cro@firm.com",
        allocationNote = null,
    )
}
