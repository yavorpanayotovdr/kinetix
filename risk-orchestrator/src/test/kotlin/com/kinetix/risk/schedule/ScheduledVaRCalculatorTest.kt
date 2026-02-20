package com.kinetix.risk.schedule

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScheduledVaRCalculatorTest : FunSpec({

    test("triggers VaR calculation at regular intervals") {
        val varService = mockk<VaRCalculationService>()

        var callCount = 0
        coEvery { varService.calculateVaR(any()) } answers {
            callCount++
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            portfolioIds = { listOf(PortfolioId("port-1")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(650)
        job.cancel()

        callCount shouldBeGreaterThanOrEqual 2
    }

    test("calculates VaR for all configured portfolios") {
        val varService = mockk<VaRCalculationService>()

        val portfoliosCalculated = mutableSetOf<String>()
        coEvery { varService.calculateVaR(any()) } answers {
            portfoliosCalculated.add(firstArg<VaRCalculationRequest>().portfolioId.value)
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            portfolioIds = { listOf(PortfolioId("port-1"), PortfolioId("port-2"), PortfolioId("port-3")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(350)
        job.cancel()

        portfoliosCalculated shouldBe setOf("port-1", "port-2", "port-3")
    }

    test("continues running even if calculation fails") {
        val varService = mockk<VaRCalculationService>()

        var callCount = 0
        coEvery { varService.calculateVaR(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("Simulated failure")
            null
        }

        val calculator = ScheduledVaRCalculator(
            varCalculationService = varService,
            portfolioIds = { listOf(PortfolioId("port-1")) },
            intervalMillis = 200,
        )

        val job = launch { calculator.start() }

        delay(650)
        job.cancel()

        callCount shouldBeGreaterThanOrEqual 2
    }
})
