package com.kinetix.risk.schedule

import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.InMemoryVaRCache
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.ValuationResult
import com.kinetix.risk.service.HierarchyRiskService
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScheduledVaRCalculatorTest : FunSpec({

    test("triggers VaR calculation at regular intervals") {
        val varService = mockk<VaRCalculationService>()
        val varCache = InMemoryVaRCache()

        var callCount = 0
        coEvery { varService.calculateVaR(any(), any()) } answers {
            callCount++
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("port-1")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(650)
        job.cancel()

        callCount shouldBeGreaterThanOrEqual 2
    }

    test("calculates VaR for all configured portfolios") {
        val varService = mockk<VaRCalculationService>()
        val varCache = InMemoryVaRCache()

        val portfoliosCalculated = mutableSetOf<String>()
        coEvery { varService.calculateVaR(any(), any()) } answers {
            portfoliosCalculated.add(firstArg<VaRCalculationRequest>().bookId.value)
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("port-1"), BookId("port-2"), BookId("port-3")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(350)
        job.cancel()

        portfoliosCalculated shouldBe setOf("port-1", "port-2", "port-3")
    }

    test("continues running even if calculation fails") {
        val varService = mockk<VaRCalculationService>()
        val varCache = InMemoryVaRCache()

        var callCount = 0
        coEvery { varService.calculateVaR(any(), any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated failure")
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("port-1")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(650)
        job.cancel()

        callCount shouldBeGreaterThanOrEqual 2
    }

    test("caches VaR results when calculation succeeds") {
        val varService = mockk<VaRCalculationService>()
        val varCache = InMemoryVaRCache()
        val mockResult = mockk<ValuationResult>()

        coEvery { varService.calculateVaR(any(), any()) } returns mockResult

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("port-1")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(100)
        job.cancel()

        varCache.get("port-1").shouldNotBeNull()
        varCache.get("port-1") shouldBe mockResult
    }

    test("triggers FIRM-level hierarchy aggregation after each per-book VaR cycle") {
        val varService = mockk<VaRCalculationService>()
        val hierarchyRiskService = mockk<HierarchyRiskService>()
        val varCache = InMemoryVaRCache()

        coEvery { varService.calculateVaR(any(), any()) } returns null
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns null

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("book-1")) },
            intervalMillis = 200,
            hierarchyRiskService = hierarchyRiskService,
        )

        val job = launch { calculator.start() }
        delay(350)
        job.cancel()

        coVerify(atLeast = 1) { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") }
    }

    test("hierarchy aggregation failure does not interrupt the cycle") {
        val varService = mockk<VaRCalculationService>()
        val hierarchyRiskService = mockk<HierarchyRiskService>()
        val varCache = InMemoryVaRCache()

        var varCallCount = 0
        coEvery { varService.calculateVaR(any(), any()) } answers {
            varCallCount++
            null
        }
        coEvery {
            hierarchyRiskService.aggregateHierarchy(any(), any())
        } throws RuntimeException("Hierarchy aggregation failure")

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            varCache = varCache,
            bookIds = { listOf(BookId("book-1")) },
            intervalMillis = 200,
            hierarchyRiskService = hierarchyRiskService,
        )

        val job = launch { calculator.start() }
        delay(650)
        job.cancel()

        // VaR calculations continued despite hierarchy failure
        varCallCount shouldBeGreaterThanOrEqual 2
    }
})
