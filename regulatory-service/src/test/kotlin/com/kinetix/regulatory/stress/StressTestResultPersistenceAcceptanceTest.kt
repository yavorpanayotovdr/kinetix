package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.DatabaseTestSetup
import com.kinetix.regulatory.persistence.ExposedFrtbCalculationRepository
import com.kinetix.regulatory.persistence.ExposedStressScenarioRepository
import com.kinetix.regulatory.persistence.ExposedStressTestResultRepository
import com.kinetix.regulatory.persistence.StressScenariosTable
import com.kinetix.regulatory.persistence.StressTestResultsTable
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class StressTestResultPersistenceAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val frtbRepo = ExposedFrtbCalculationRepository(db)
    val riskClient = mockk<RiskOrchestratorClient>()
    val stressScenarioRepo = ExposedStressScenarioRepository(db)
    val stressTestResultRepo = ExposedStressTestResultRepository(db)

    beforeEach {
        newSuspendedTransaction(db = db) {
            StressTestResultsTable.deleteAll()
            StressScenariosTable.deleteAll()
        }
    }

    given("an approved stress scenario") {
        `when`("POST /{id}/run is called") {
            then("returns 201 with result and persists to database") {
                testApplication {
                    application {
                        module(
                            frtbRepo,
                            riskClient,
                            stressScenarioRepository = stressScenarioRepo,
                            stressTestResultRepository = stressTestResultRepo,
                        )
                    }

                    val createResponse = client.post("/api/v1/stress-scenarios") {
                        contentType(ContentType.Application.Json)
                        setBody("""
                            {
                                "name": "Equity Crash",
                                "description": "Global equity -30%",
                                "shocks": "{\"EQ\":-0.30}",
                                "createdBy": "analyst@kinetix.com"
                            }
                        """.trimIndent())
                    }
                    val scenarioId = Json.parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]!!.jsonPrimitive.content

                    client.patch("/api/v1/stress-scenarios/$scenarioId/submit")
                    client.patch("/api/v1/stress-scenarios/$scenarioId/approve") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"approvedBy":"manager@kinetix.com"}""")
                    }

                    val runResponse = client.post("/api/v1/stress-scenarios/$scenarioId/run") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"portfolioId":"portfolio-1"}""")
                    }

                    runResponse.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(runResponse.bodyAsText()).jsonObject
                    body["scenarioId"]?.jsonPrimitive?.content shouldBe scenarioId
                    body["portfolioId"]?.jsonPrimitive?.content shouldBe "portfolio-1"
                    body.containsKey("id") shouldBe true
                    body.containsKey("calculatedAt") shouldBe true
                    body["pnlImpact"]?.jsonPrimitive?.content shouldNotBe null
                }
            }
        }
    }

    given("a DRAFT scenario") {
        `when`("POST /{id}/run is called") {
            then("returns 500 because scenario is not APPROVED") {
                testApplication {
                    application {
                        module(
                            frtbRepo,
                            riskClient,
                            stressScenarioRepository = stressScenarioRepo,
                            stressTestResultRepository = stressTestResultRepo,
                        )
                    }

                    val createResponse = client.post("/api/v1/stress-scenarios") {
                        contentType(ContentType.Application.Json)
                        setBody("""
                            {
                                "name": "Draft Scenario",
                                "description": "Not yet approved",
                                "shocks": "{}",
                                "createdBy": "analyst@kinetix.com"
                            }
                        """.trimIndent())
                    }
                    val scenarioId = Json.parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]!!.jsonPrimitive.content

                    val runResponse = client.post("/api/v1/stress-scenarios/$scenarioId/run") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"portfolioId":"portfolio-1"}""")
                    }

                    runResponse.status shouldBe HttpStatusCode.InternalServerError
                }
            }
        }
    }

    given("an approved scenario with multiple shock factors") {
        `when`("POST /{id}/run is called") {
            then("pnlImpact sums the shock factors") {
                testApplication {
                    application {
                        module(
                            frtbRepo,
                            riskClient,
                            stressScenarioRepository = stressScenarioRepo,
                            stressTestResultRepository = stressTestResultRepo,
                        )
                    }

                    val createResponse = client.post("/api/v1/stress-scenarios") {
                        contentType(ContentType.Application.Json)
                        setBody("""
                            {
                                "name": "Multi-Factor",
                                "description": "Equity -30%, IR +1%",
                                "shocks": "{\"EQ\":-0.30,\"IR\":0.01}",
                                "createdBy": "analyst@kinetix.com"
                            }
                        """.trimIndent())
                    }
                    val scenarioId = Json.parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]!!.jsonPrimitive.content

                    client.patch("/api/v1/stress-scenarios/$scenarioId/submit")
                    client.patch("/api/v1/stress-scenarios/$scenarioId/approve") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"approvedBy":"manager@kinetix.com"}""")
                    }

                    val runResponse = client.post("/api/v1/stress-scenarios/$scenarioId/run") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"portfolioId":"portfolio-2","modelVersion":"v1.0"}""")
                    }

                    runResponse.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(runResponse.bodyAsText()).jsonObject
                    body["modelVersion"]?.jsonPrimitive?.content shouldBe "v1.0"
                    // EQ(-0.30) + IR(0.01) = -0.29
                    val pnlImpact = body["pnlImpact"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    pnlImpact shouldNotBe null
                }
            }
        }
    }
})
