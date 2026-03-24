package com.kinetix.gateway.contract

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class GatewayRiskBudgetContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    val sampleAllocation = Json.parseToJsonElement(
        """
        {
          "id":"alloc-1","entityLevel":"DESK","entityId":"desk-rates",
          "budgetType":"VAR_BUDGET","budgetPeriod":"DAILY",
          "budgetAmount":"5000000.00","effectiveFrom":"2026-01-01",
          "effectiveTo":null,"allocatedBy":"cro@firm.com","allocationNote":null
        }
        """.trimIndent()
    ).jsonObject

    given("gateway routing to risk budget endpoints") {

        `when`("GET /api/v1/risk/budgets returns budget list") {
            then("returns 200 with array of allocations") {
                coEvery { riskClient.getRiskBudgets(null, null) } returns buildJsonArray {
                    add(sampleAllocation)
                }

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/budgets")

                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "alloc-1"
                }
            }
        }

        `when`("GET /api/v1/risk/budgets/{id} finds allocation") {
            then("returns 200 with allocation details") {
                coEvery { riskClient.getRiskBudget("alloc-1") } returns sampleAllocation

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/budgets/alloc-1")

                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["entityLevel"]?.jsonPrimitive?.content shouldBe "DESK"
                    body["budgetType"]?.jsonPrimitive?.content shouldBe "VAR_BUDGET"
                    body["budgetAmount"]?.jsonPrimitive?.content shouldBe "5000000.00"
                }
            }
        }

        `when`("GET /api/v1/risk/budgets/{id} budget not found") {
            then("returns 404") {
                coEvery { riskClient.getRiskBudget("nonexistent") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/budgets/nonexistent")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("POST /api/v1/risk/budgets creates a budget") {
            then("returns 201 with created allocation") {
                coEvery { riskClient.createRiskBudget(any()) } returns sampleAllocation

                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/risk/budgets") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"entityLevel":"DESK","entityId":"desk-rates","budgetType":"VAR_BUDGET",
                             "budgetPeriod":"DAILY","budgetAmount":"5000000.00",
                             "effectiveFrom":"2026-01-01","allocatedBy":"cro@firm.com"}
                            """.trimIndent()
                        )
                    }

                    response.status shouldBe HttpStatusCode.Created

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("id") shouldBe true
                }
            }
        }

        `when`("DELETE /api/v1/risk/budgets/{id} removes an allocation") {
            then("returns 204") {
                coEvery { riskClient.deleteRiskBudget("alloc-1") } returns true

                testApplication {
                    application { module(riskClient) }
                    val response = client.delete("/api/v1/risk/budgets/alloc-1")
                    response.status shouldBe HttpStatusCode.NoContent
                }
            }
        }

        `when`("DELETE /api/v1/risk/budgets/{id} when not found") {
            then("returns 404") {
                coEvery { riskClient.deleteRiskBudget("nonexistent") } returns false

                testApplication {
                    application { module(riskClient) }
                    val response = client.delete("/api/v1/risk/budgets/nonexistent")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
})
