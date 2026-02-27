package com.kinetix.gateway.contract

import com.kinetix.common.model.*
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

class GatewayRiskContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    given("gateway routing to risk-orchestrator") {

        `when`("POST /api/v1/risk/var/{portfolioId} with valid request") {
            then("returns 200 with VaR response shape including componentBreakdown") {
                coEvery { riskClient.calculateVaR(any()) } returns ValuationResultSummary(
                    portfolioId = "port-1",
                    calculationType = "PARAMETRIC",
                    confidenceLevel = "CL_95",
                    varValue = 50000.0,
                    expectedShortfall = 65000.0,
                    componentBreakdown = listOf(
                        ComponentBreakdownItem("EQUITY", 30000.0, 60.0),
                    ),
                    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
                    pvValue = 1250000.0,
                )

                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/var/port-1") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
                    body["calculationType"]?.jsonPrimitive?.content shouldBe "PARAMETRIC"
                    body["confidenceLevel"]?.jsonPrimitive?.content shouldBe "CL_95"
                    body.containsKey("varValue") shouldBe true
                    body.containsKey("expectedShortfall") shouldBe true
                    body.containsKey("componentBreakdown") shouldBe true
                    body["componentBreakdown"]?.jsonArray?.size shouldBe 1
                    val comp = body["componentBreakdown"]?.jsonArray?.get(0)?.jsonObject
                    comp?.containsKey("assetClass") shouldBe true
                    comp?.containsKey("varContribution") shouldBe true
                    comp?.containsKey("percentageOfTotal") shouldBe true
                }
            }
        }

        `when`("POST /api/v1/risk/var/{portfolioId} with invalid calculationType") {
            then("returns 400 with error shape") {
                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/var/port-1") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"calculationType":"INVALID_TYPE"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.bodyAsText()
                    body shouldContain "error"
                }
            }
        }
    }
})
