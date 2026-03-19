package com.kinetix.gateway.contract

import com.kinetix.gateway.client.*
import com.kinetix.gateway.dto.CrossBookVaRCalculationParams
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

class GatewayCrossBookVaRContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    given("gateway routing to cross-book VaR endpoints") {

        `when`("POST /api/v1/risk/var/cross-book with valid request") {
            then("returns 200 with cross-book VaR response shape") {
                coEvery { riskClient.calculateCrossBookVaR(any()) } returns CrossBookVaRResultSummary(
                    portfolioGroupId = "desk-fx",
                    bookIds = listOf("book-1", "book-2"),
                    calculationType = "PARAMETRIC",
                    confidenceLevel = "CL_95",
                    varValue = 120000.0,
                    expectedShortfall = 155000.0,
                    componentBreakdown = listOf(
                        ComponentBreakdownItem("EQUITY", 80000.0, 66.67),
                    ),
                    bookContributions = listOf(
                        BookVaRContributionSummary(
                            bookId = "book-1",
                            varContribution = 70000.0,
                            percentageOfTotal = 58.33,
                            standaloneVar = 85000.0,
                            diversificationBenefit = 15000.0,
                            marginalVar = 0.000045,
                        ),
                        BookVaRContributionSummary(
                            bookId = "book-2",
                            varContribution = 50000.0,
                            percentageOfTotal = 41.67,
                            standaloneVar = 65000.0,
                            diversificationBenefit = 15000.0,
                            marginalVar = 0.000032,
                        ),
                    ),
                    totalStandaloneVar = 150000.0,
                    diversificationBenefit = 30000.0,
                    calculatedAt = Instant.parse("2025-06-15T10:00:00Z"),
                )

                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/var/cross-book") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"bookIds":["book-1","book-2"],"portfolioGroupId":"desk-fx"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["portfolioGroupId"]?.jsonPrimitive?.content shouldBe "desk-fx"
                    body["bookIds"]?.jsonArray?.size shouldBe 2
                    body.containsKey("varValue") shouldBe true
                    body.containsKey("expectedShortfall") shouldBe true
                    body.containsKey("calculatedAt") shouldBe true
                    body.containsKey("totalStandaloneVar") shouldBe true
                    body.containsKey("diversificationBenefit") shouldBe true

                    val contributions = body["bookContributions"]?.jsonArray
                    contributions?.size shouldBe 2
                    val first = contributions?.get(0)?.jsonObject
                    first?.containsKey("bookId") shouldBe true
                    first?.containsKey("varContribution") shouldBe true
                    first?.containsKey("percentageOfTotal") shouldBe true
                    first?.containsKey("standaloneVar") shouldBe true
                    first?.containsKey("diversificationBenefit") shouldBe true
                    first?.containsKey("marginalVar") shouldBe true
                }
            }
        }

        `when`("POST /api/v1/risk/var/cross-book with empty bookIds") {
            then("returns 400") {
                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/var/cross-book") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"bookIds":[],"portfolioGroupId":"desk"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.bodyAsText()
                    body shouldContain "error"
                }
            }
        }

        `when`("POST /api/v1/risk/var/cross-book with invalid confidence level") {
            then("returns 400") {
                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/var/cross-book") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"bookIds":["book-1"],"portfolioGroupId":"desk","confidenceLevel":"CL_50"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.bodyAsText()
                    body shouldContain "error"
                }
            }
        }

        `when`("GET /api/v1/risk/var/cross-book/{groupId} with cached result") {
            then("returns 200 with correct fields") {
                coEvery { riskClient.getCrossBookVaR("desk-fx") } returns CrossBookVaRResultSummary(
                    portfolioGroupId = "desk-fx",
                    bookIds = listOf("book-1"),
                    calculationType = "PARAMETRIC",
                    confidenceLevel = "CL_99",
                    varValue = 90000.0,
                    expectedShortfall = 110000.0,
                    componentBreakdown = emptyList(),
                    bookContributions = listOf(
                        BookVaRContributionSummary(
                            bookId = "book-1",
                            varContribution = 90000.0,
                            percentageOfTotal = 100.0,
                            standaloneVar = 90000.0,
                            diversificationBenefit = 0.0,
                            marginalVar = 0.0,
                        ),
                    ),
                    totalStandaloneVar = 90000.0,
                    diversificationBenefit = 0.0,
                    calculatedAt = Instant.parse("2025-06-15T12:00:00Z"),
                )

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/var/cross-book/desk-fx")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["portfolioGroupId"]?.jsonPrimitive?.content shouldBe "desk-fx"
                    body.containsKey("varValue") shouldBe true
                    body.containsKey("expectedShortfall") shouldBe true
                    body.containsKey("bookContributions") shouldBe true
                    body.containsKey("calculatedAt") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/risk/var/cross-book/{groupId} when not found") {
            then("returns 404") {
                coEvery { riskClient.getCrossBookVaR("unknown-group") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/var/cross-book/unknown-group")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("POST /api/v1/risk/var/port-1 (single-book VaR endpoint)") {
            then("still works after cross-book changes — regression test") {
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
                    body.containsKey("varValue") shouldBe true
                    body.containsKey("expectedShortfall") shouldBe true
                    body.containsKey("componentBreakdown") shouldBe true
                }
            }
        }
    }
})
