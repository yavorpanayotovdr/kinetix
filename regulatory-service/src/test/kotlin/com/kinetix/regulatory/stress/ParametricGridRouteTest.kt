package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.client.StressTestResultDto
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk

class ParametricGridRouteTest : FunSpec({

    val riskClient = mockk<RiskOrchestratorClient>()
    val stressRepo = mockk<StressScenarioRepository>()

    test("POST /api/v1/stress-scenarios/parametric-grid returns 200 with grid results") {
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "-1000.00")

        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    stressScenarioRepository = stressRepo,
                )
            }
            val response = client.post("/api/v1/stress-scenarios/parametric-grid") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "bookId": "BOOK-1",
                      "primaryAxis": "equity",
                      "primaryRange": [-0.10, -0.20, -0.30],
                      "secondaryAxis": "volatility",
                      "secondaryRange": [0.10, 0.20, 0.30]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"primaryAxis\":\"equity\""
            body shouldContain "\"secondaryAxis\":\"volatility\""
            body shouldContain "\"cells\""
            body shouldContain "\"pnlImpact\":\"-1000.00\""
        }
    }

    test("POST /api/v1/stress-scenarios/parametric-grid 3x3 grid returns 9 cells") {
        coEvery { riskClient.runStressTest(any(), any(), any()) } returns StressTestResultDto(pnlImpact = "-500.00")

        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    stressScenarioRepository = stressRepo,
                )
            }
            val response = client.post("/api/v1/stress-scenarios/parametric-grid") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "bookId": "BOOK-1",
                      "primaryAxis": "equity",
                      "primaryRange": [-0.10, -0.20, -0.30],
                      "secondaryAxis": "credit_spread",
                      "secondaryRange": [0.01, 0.02, 0.03]
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            // 9 cells means 9 occurrences of "pnlImpact" in the cells array
            body.split("\"pnlImpact\"").size - 1 shouldBe 9
        }
    }
})
