package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import com.kinetix.regulatory.stress.dto.InstrumentShock
import com.kinetix.regulatory.stress.dto.ReverseStressResultResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk

class ReverseStressRouteTest : FunSpec({

    val scenarioRepository = mockk<StressScenarioRepository>()
    val service = StressScenarioService(scenarioRepository)
    val riskClient = mockk<RiskOrchestratorClient>()

    test("POST /api/v1/stress-scenarios/reverse-stress returns shocks from risk-orchestrator") {
        coEvery {
            riskClient.runReverseStress(
                bookId = "BOOK-1",
                targetLoss = 1_000_000.0,
                maxShock = -1.0,
            )
        } returns ReverseStressResultResponse(
            shocks = listOf(
                InstrumentShock(instrumentId = "AAPL", shock = "-0.090909"),
                InstrumentShock(instrumentId = "MSFT", shock = "-0.090909"),
            ),
            achievedLoss = "1000000.00",
            targetLoss = "1000000.00",
            converged = true,
            calculatedAt = "2026-03-25T12:00:00Z",
        )

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service, riskClient) }
            }
            val response = client.post("/api/v1/stress-scenarios/reverse-stress") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","targetLoss":1000000.0,"maxShock":-1.0}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"converged\":true"
            body shouldContain "\"achievedLoss\":\"1000000.00\""
            body shouldContain "\"targetLoss\":\"1000000.00\""
            body shouldContain "\"instrumentId\":\"AAPL\""
        }
    }

    test("POST /api/v1/stress-scenarios/reverse-stress returns 400 when targetLoss is not positive") {
        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service, riskClient) }
            }
            val response = client.post("/api/v1/stress-scenarios/reverse-stress") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","targetLoss":-500.0,"maxShock":-1.0}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/stress-scenarios/reverse-stress uses default maxShock of -1.0 when not supplied") {
        coEvery {
            riskClient.runReverseStress(
                bookId = "BOOK-1",
                targetLoss = 500_000.0,
                maxShock = -1.0,
            )
        } returns ReverseStressResultResponse(
            shocks = emptyList(),
            achievedLoss = "500000.00",
            targetLoss = "500000.00",
            converged = true,
            calculatedAt = "2026-03-25T12:00:00Z",
        )

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service, riskClient) }
            }
            val response = client.post("/api/v1/stress-scenarios/reverse-stress") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","targetLoss":500000.0}""")
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "\"converged\":true"
        }
    }
})
