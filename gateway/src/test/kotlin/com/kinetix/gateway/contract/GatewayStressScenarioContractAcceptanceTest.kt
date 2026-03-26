package com.kinetix.gateway.contract

import com.kinetix.common.security.Role
import com.kinetix.gateway.auth.TestJwtHelper
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class GatewayStressScenarioContractAcceptanceTest : BehaviorSpec({

    val regulatoryClient = mockk<RegulatoryServiceClient>()
    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()
    val riskManagerToken = TestJwtHelper.generateToken(userId = "manager@kinetix.com", roles = listOf(Role.RISK_MANAGER))

    beforeEach { clearMocks(regulatoryClient) }

    given("gateway routing to regulatory-service stress scenarios") {

        `when`("GET /api/v1/stress-scenarios") {
            then("returns array with expected JSON shape") {
                coEvery { regulatoryClient.listScenarios() } returns listOf(
                    StressScenarioItem(
                        id = "sc-1",
                        name = "Equity Crash",
                        description = "Global equity -30%",
                        shocks = """{"EQ":-0.30}""",
                        status = "APPROVED",
                        createdBy = "analyst@kinetix.com",
                        approvedBy = "manager@kinetix.com",
                        approvedAt = "2026-01-15T10:00:00Z",
                        createdAt = "2026-01-10T08:00:00Z",
                    ),
                )

                testApplication {
                    application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
                    val response = client.get("/api/v1/stress-scenarios") {
                        header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val array = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    array.size shouldBe 1
                    val item = array[0].jsonObject
                    item["id"]?.jsonPrimitive?.content shouldBe "sc-1"
                    item["name"]?.jsonPrimitive?.content shouldBe "Equity Crash"
                    item["description"]?.jsonPrimitive?.content shouldBe "Global equity -30%"
                    item["shocks"]?.jsonPrimitive?.content shouldBe """{"EQ":-0.30}"""
                    item["status"]?.jsonPrimitive?.content shouldBe "APPROVED"
                    item["createdBy"]?.jsonPrimitive?.content shouldBe "analyst@kinetix.com"
                    item["approvedBy"]?.jsonPrimitive?.content shouldBe "manager@kinetix.com"
                    item.containsKey("approvedAt") shouldBe true
                    item.containsKey("createdAt") shouldBe true
                }
            }
        }

        `when`("POST /api/v1/stress-scenarios with valid body") {
            then("returns 201 with scenario response shape") {
                coEvery { regulatoryClient.createScenario(any()) } returns StressScenarioItem(
                    id = "sc-new",
                    name = "FX Shock",
                    description = "USD/EUR +15%",
                    shocks = """{"FX":0.15}""",
                    status = "DRAFT",
                    createdBy = "analyst@kinetix.com",
                    approvedBy = null,
                    approvedAt = null,
                    createdAt = "2026-01-15T10:00:00Z",
                )

                testApplication {
                    application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
                    val response = client.post("/api/v1/stress-scenarios") {
                        header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""
                            {
                                "name": "FX Shock",
                                "description": "USD/EUR +15%",
                                "shocks": "{\"FX\":0.15}"
                            }
                        """.trimIndent())
                    }
                    response.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["id"]?.jsonPrimitive?.content shouldBe "sc-new"
                    body["status"]?.jsonPrimitive?.content shouldBe "DRAFT"
                    body["name"]?.jsonPrimitive?.content shouldBe "FX Shock"
                    body["approvedBy"] shouldBe JsonNull
                    body["approvedAt"] shouldBe JsonNull
                }
            }
        }

        `when`("PATCH /{id}/approve with approvedBy") {
            then("returns 200 with APPROVED status and approver") {
                coEvery { regulatoryClient.approve("sc-1", any()) } returns StressScenarioItem(
                    id = "sc-1",
                    name = "Equity Crash",
                    description = "Global equity -30%",
                    shocks = """{"EQ":-0.30}""",
                    status = "APPROVED",
                    createdBy = "analyst@kinetix.com",
                    approvedBy = "manager@kinetix.com",
                    approvedAt = "2026-01-16T12:00:00Z",
                    createdAt = "2026-01-10T08:00:00Z",
                )

                testApplication {
                    application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
                    val response = client.patch("/api/v1/stress-scenarios/sc-1/approve") {
                        header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["status"]?.jsonPrimitive?.content shouldBe "APPROVED"
                    body["approvedBy"]?.jsonPrimitive?.content shouldBe "manager@kinetix.com"
                    body.containsKey("approvedAt") shouldBe true
                }
            }
        }

        `when`("PATCH /{id}/retire") {
            then("returns 200 with RETIRED status") {
                coEvery { regulatoryClient.retire("sc-1") } returns StressScenarioItem(
                    id = "sc-1",
                    name = "Equity Crash",
                    description = "Global equity -30%",
                    shocks = """{"EQ":-0.30}""",
                    status = "RETIRED",
                    createdBy = "analyst@kinetix.com",
                    approvedBy = "manager@kinetix.com",
                    approvedAt = "2026-01-16T12:00:00Z",
                    createdAt = "2026-01-10T08:00:00Z",
                )

                testApplication {
                    application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
                    val response = client.patch("/api/v1/stress-scenarios/sc-1/retire") {
                        header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["status"]?.jsonPrimitive?.content shouldBe "RETIRED"
                    body["id"]?.jsonPrimitive?.content shouldBe "sc-1"
                }
            }
        }
    }
})
