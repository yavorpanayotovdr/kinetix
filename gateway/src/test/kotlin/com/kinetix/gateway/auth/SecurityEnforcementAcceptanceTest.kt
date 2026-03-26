package com.kinetix.gateway.auth

import com.kinetix.common.security.Role
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json

class SecurityEnforcementAcceptanceTest : FunSpec({

    val regulatoryClient = mockk<RegulatoryServiceClient>()
    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()

    beforeEach { clearMocks(regulatoryClient) }

    test("unauthenticated request to stress-scenarios returns 401") {
        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }

            val response = client.get("/api/v1/stress-scenarios")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("VIEWER cannot create a stress scenario (403)") {
        val token = TestJwtHelper.generateToken(roles = listOf(Role.VIEWER))

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }

            val response = client.post("/api/v1/stress-scenarios") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Shock","description":"desc","shocks":"{}"}""")
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("COMPLIANCE user cannot create a stress scenario (403)") {
        val token = TestJwtHelper.generateToken(roles = listOf(Role.COMPLIANCE))

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }

            val response = client.post("/api/v1/stress-scenarios") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Shock","description":"desc","shocks":"{}"}""")
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("RISK_MANAGER can create a stress scenario with createdBy from JWT") {
        val capturedParams = slot<CreateScenarioParams>()
        coEvery { regulatoryClient.createScenario(capture(capturedParams)) } returns StressScenarioItem(
            id = "sc-1",
            name = "Shock",
            description = "desc",
            shocks = "{}",
            status = "DRAFT",
            createdBy = "risk-mgr-1",
            approvedBy = null,
            approvedAt = null,
            createdAt = "2026-01-01T00:00:00Z",
        )

        val token = TestJwtHelper.generateToken(userId = "risk-mgr-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }

            val response = client.post("/api/v1/stress-scenarios") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Shock","description":"desc","shocks":"{}"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            // createdBy should come from JWT principal, NOT from request body
            capturedParams.captured.createdBy shouldBe "risk-mgr-1"
        }
    }

    test("RISK_MANAGER can approve a scenario with approvedBy from JWT") {
        val capturedParams = slot<ApproveScenarioParams>()
        coEvery { regulatoryClient.approve("sc-1", capture(capturedParams)) } returns StressScenarioItem(
            id = "sc-1",
            name = "Shock",
            description = "desc",
            shocks = "{}",
            status = "APPROVED",
            createdBy = "analyst-1",
            approvedBy = "risk-mgr-1",
            approvedAt = "2026-01-02T00:00:00Z",
            createdAt = "2026-01-01T00:00:00Z",
        )

        val token = TestJwtHelper.generateToken(userId = "risk-mgr-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, regulatoryClient = regulatoryClient, jwkProvider = jwkProvider) }

            val response = client.patch("/api/v1/stress-scenarios/sc-1/approve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            response.status shouldBe HttpStatusCode.OK
            // approvedBy should come from JWT principal, NOT from request body
            capturedParams.captured.approvedBy shouldBe "risk-mgr-1"
        }
    }

    test("unauthenticated request to audit routes returns 401") {
        val mockHttpClient = HttpClient(MockEngine { respond("[]", HttpStatusCode.OK) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        testApplication {
            application {
                module(
                    jwtConfig,
                    regulatoryClient = regulatoryClient,
                    httpClient = mockHttpClient,
                    auditBaseUrl = "http://audit-service",
                    jwkProvider = jwkProvider,
                )
            }
            val response = client.get("/api/v1/audit/events")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
