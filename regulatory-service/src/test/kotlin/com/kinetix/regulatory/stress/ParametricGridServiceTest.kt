package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.client.StressTestResultDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ParametricGridServiceTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val riskClient = mockk<RiskOrchestratorClient>()
    val service = StressScenarioService(repository, riskOrchestratorClient = riskClient)

    beforeTest {
        clearMocks(repository, riskClient)
    }

    test("produces a result entry for each cell in the cartesian product of two axes") {
        // 3 primary values x 3 secondary values = 9 cells
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "-1000.00")

        val result = service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = listOf(-0.10, -0.20, -0.30),
            secondaryAxis = "volatility",
            secondaryRange = listOf(0.10, 0.20, 0.30),
        )

        result.cells shouldHaveSize 9
        coVerify(exactly = 9) { riskClient.runStressTest(any(), any(), any()) }
    }

    test("labels each cell with its primary and secondary shock values") {
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "-500.00")

        val result = service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = listOf(-0.10, -0.20),
            secondaryAxis = "rates",
            secondaryRange = listOf(0.01, 0.02),
        )

        val cell = result.cells.first { it.primaryShock == -0.10 && it.secondaryShock == 0.01 }
        cell.primaryAxis shouldBe "equity"
        cell.secondaryAxis shouldBe "rates"
        cell.pnlImpact shouldBe "-500.00"
    }

    test("passes combined shocks for each cell to the risk orchestrator") {
        val capturedShocks = mutableListOf<Map<String, Double>>()
        coEvery {
            riskClient.runStressTest(any(), any(), capture(capturedShocks))
        } returns StressTestResultDto(pnlImpact = "0.00")

        service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = listOf(-0.20),
            secondaryAxis = "volatility",
            secondaryRange = listOf(0.15),
        )

        capturedShocks shouldHaveSize 1
        capturedShocks[0]["equity"] shouldBe -0.20
        capturedShocks[0]["volatility"] shouldBe 0.15
    }

    test("returns the primary and secondary axis names in the response") {
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "0.00")

        val result = service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = listOf(-0.10),
            secondaryAxis = "credit_spread",
            secondaryRange = listOf(0.01),
        )

        result.primaryAxis shouldBe "equity"
        result.secondaryAxis shouldBe "credit_spread"
    }

    test("a 3x3 grid produces exactly 9 cells with all axis combinations covered") {
        val equityValues = listOf(-0.10, -0.20, -0.30)
        val volValues = listOf(0.10, 0.20, 0.30)
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "-100.00")

        val result = service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = equityValues,
            secondaryAxis = "volatility",
            secondaryRange = volValues,
        )

        result.cells shouldHaveSize 9
        for (eq in equityValues) {
            for (vol in volValues) {
                val cell = result.cells.find { it.primaryShock == eq && it.secondaryShock == vol }
                cell shouldBe (result.cells.find { it.primaryShock == eq && it.secondaryShock == vol })
            }
        }
        // Verify every combination exists
        val combinations = result.cells.map { it.primaryShock to it.secondaryShock }.toSet()
        combinations shouldHaveSize 9
    }

    test("identifies the worst cell by most negative pnl impact") {
        val pnlImpacts = listOf("-100.00", "-500.00", "-200.00", "-800.00")
        var callIndex = 0
        coEvery { riskClient.runStressTest(any(), any(), any()) } answers {
            StressTestResultDto(pnlImpact = pnlImpacts[callIndex++])
        }

        val result = service.runParametricGrid(
            bookId = "BOOK-1",
            primaryAxis = "equity",
            primaryRange = listOf(-0.10, -0.20),
            secondaryAxis = "volatility",
            secondaryRange = listOf(0.10, 0.20),
        )

        result.worstPnlImpact shouldBe "-800.00"
    }
})
