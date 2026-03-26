package com.kinetix.gateway.routes

import com.kinetix.common.security.Role
import com.kinetix.gateway.auth.TestJwtHelper
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

private val sampleScenario = StressScenarioItem(
    id = "scenario-1",
    name = "GFC Replay",
    description = "2008 crisis replay",
    shocks = """{"volShocks":{"EQUITY":3.0},"priceShocks":{"EQUITY":0.6}}""",
    status = "DRAFT",
    createdBy = "analyst@kinetix.com",
    approvedBy = null,
    approvedAt = null,
    createdAt = "2026-03-03T08:00:00Z",
)

class StressScenarioRoutesTest : FunSpec({

    val regulatoryClient = mockk<RegulatoryServiceClient>()
    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()
    val riskManagerToken = TestJwtHelper.generateToken(userId = "head@kinetix.com", roles = listOf(Role.RISK_MANAGER))
    val analystToken = TestJwtHelper.generateToken(userId = "analyst@kinetix.com", roles = listOf(Role.RISK_MANAGER))

    beforeEach {
        clearMocks(regulatoryClient)
    }

    test("GET /api/v1/stress-scenarios returns all scenarios") {
        coEvery { regulatoryClient.listScenarios() } returns listOf(sampleScenario)

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.get("/api/v1/stress-scenarios") {
                header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "scenario-1"
            body[0].jsonObject["name"]?.jsonPrimitive?.content shouldBe "GFC Replay"
            body[0].jsonObject["status"]?.jsonPrimitive?.content shouldBe "DRAFT"
        }
    }

    test("GET /api/v1/stress-scenarios/approved returns approved scenarios") {
        val approved = sampleScenario.copy(status = "APPROVED", approvedBy = "head@kinetix.com")
        coEvery { regulatoryClient.listApprovedScenarios() } returns listOf(approved)

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.get("/api/v1/stress-scenarios/approved") {
                header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["status"]?.jsonPrimitive?.content shouldBe "APPROVED"
        }
    }

    test("POST /api/v1/stress-scenarios creates scenario and uses JWT principal as createdBy") {
        val capturedParams = slot<CreateScenarioParams>()
        coEvery { regulatoryClient.createScenario(capture(capturedParams)) } returns sampleScenario

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.post("/api/v1/stress-scenarios") {
                header(HttpHeaders.Authorization, "Bearer $analystToken")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"GFC Replay","description":"2008 crisis replay","shocks":"{}"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "scenario-1"
            body["name"]?.jsonPrimitive?.content shouldBe "GFC Replay"
            capturedParams.captured.createdBy shouldBe "analyst@kinetix.com"
        }
    }

    test("PATCH /api/v1/stress-scenarios/{id}/submit submits for approval") {
        val submitted = sampleScenario.copy(status = "PENDING_APPROVAL")
        coEvery { regulatoryClient.submitForApproval("scenario-1") } returns submitted

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.patch("/api/v1/stress-scenarios/scenario-1/submit") {
                header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "PENDING_APPROVAL"
        }
    }

    test("PATCH /api/v1/stress-scenarios/{id}/approve uses JWT principal as approvedBy") {
        val approved = sampleScenario.copy(status = "APPROVED", approvedBy = "head@kinetix.com")
        val capturedParams = slot<ApproveScenarioParams>()
        coEvery { regulatoryClient.approve("scenario-1", capture(capturedParams)) } returns approved

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.patch("/api/v1/stress-scenarios/scenario-1/approve") {
                header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "APPROVED"
            body["approvedBy"]?.jsonPrimitive?.content shouldBe "head@kinetix.com"
            capturedParams.captured.approvedBy shouldBe "head@kinetix.com"
        }
    }

    test("PATCH /api/v1/stress-scenarios/{id}/retire retires scenario") {
        val retired = sampleScenario.copy(status = "RETIRED")
        coEvery { regulatoryClient.retire("scenario-1") } returns retired

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }
            val response = client.patch("/api/v1/stress-scenarios/scenario-1/retire") {
                header(HttpHeaders.Authorization, "Bearer $riskManagerToken")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "RETIRED"
        }
    }
})
