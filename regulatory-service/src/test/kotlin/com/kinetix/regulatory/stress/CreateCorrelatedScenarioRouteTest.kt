package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.CorrelationMatrix
import com.kinetix.regulatory.client.CorrelationServiceClient
import com.kinetix.regulatory.client.RiskOrchestratorClient
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

class CreateCorrelatedScenarioRouteTest : FunSpec({

    val riskClient = mockk<RiskOrchestratorClient>()
    val correlationClient = mockk<CorrelationServiceClient>()
    val stressRepo = mockk<StressScenarioRepository>()

    val aCorrelationMatrix = CorrelationMatrix(
        labels = listOf("EQUITY", "RATES", "CREDIT"),
        values = listOf(
            1.0, -0.3, 0.5,
            -0.3, 1.0, -0.2,
            0.5, -0.2, 1.0,
        ),
    )

    test("POST /api/v1/stress-scenarios/correlated creates a scenario and returns 201") {
        coEvery { stressRepo.save(any()) } returns Unit
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns aCorrelationMatrix

        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    stressScenarioRepository = stressRepo,
                    correlationServiceClient = correlationClient,
                )
            }
            val response = client.post("/api/v1/stress-scenarios/correlated") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "name": "Equity crash correlated",
                      "description": "Equity shock with derived secondary shocks",
                      "primaryAssetClass": "EQUITY",
                      "primaryShock": -0.20,
                      "assetClasses": ["EQUITY", "RATES", "CREDIT"],
                      "createdBy": "analyst-1"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"name\":\"Equity crash correlated\""
            body shouldContain "\"status\":\"DRAFT\""
            body shouldContain "\"scenarioType\":\"PARAMETRIC\""
        }
    }

    test("POST /api/v1/stress-scenarios/correlated returns 400 when primary asset class not in list") {
        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    stressScenarioRepository = stressRepo,
                    correlationServiceClient = correlationClient,
                )
            }
            val response = client.post("/api/v1/stress-scenarios/correlated") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "name": "Bad request",
                      "description": "FX not in asset classes",
                      "primaryAssetClass": "FX",
                      "primaryShock": -0.15,
                      "assetClasses": ["EQUITY", "RATES"],
                      "createdBy": "analyst-1"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/stress-scenarios/correlated returns 400 when correlation matrix unavailable") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns null

        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    stressScenarioRepository = stressRepo,
                    correlationServiceClient = correlationClient,
                )
            }
            val response = client.post("/api/v1/stress-scenarios/correlated") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "name": "No matrix",
                      "description": "Should fail",
                      "primaryAssetClass": "EQUITY",
                      "primaryShock": -0.20,
                      "assetClasses": ["EQUITY", "RATES"],
                      "createdBy": "analyst-1"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
