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

class GatewayFactorRiskContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    val sampleResponse = buildJsonObject {
        put("bookId", "BOOK-1")
        put("calculatedAt", "2026-03-24T10:00:00Z")
        put("totalVar", 50_000.0)
        put("systematicVar", 38_000.0)
        put("idiosyncraticVar", 12_000.0)
        put("rSquared", 0.576)
        put("concentrationWarning", false)
        putJsonArray("factors") {
            addJsonObject {
                put("factorType", "EQUITY_BETA")
                put("varContribution", 30_000.0)
                put("pctOfTotal", 0.60)
                put("loading", 1.2)
                put("loadingMethod", "OLS_REGRESSION")
            }
        }
    }

    given("gateway routing to factor risk endpoints") {

        `when`("GET /api/v1/books/{bookId}/factor-risk/latest with existing snapshot") {
            then("returns 200 with the factor decomposition snapshot") {
                coEvery { riskClient.getLatestFactorRisk("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/BOOK-1/factor-risk/latest")

                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                    body.containsKey("totalVar") shouldBe true
                    body.containsKey("systematicVar") shouldBe true
                    body.containsKey("idiosyncraticVar") shouldBe true
                    body.containsKey("rSquared") shouldBe true
                    body.containsKey("concentrationWarning") shouldBe true
                    body.containsKey("factors") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/factor-risk/latest when no snapshot exists") {
            then("returns 404") {
                coEvery { riskClient.getLatestFactorRisk("UNKNOWN") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/UNKNOWN/factor-risk/latest")

                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/factor-risk returns history") {
            then("returns 200 with array of snapshots") {
                val history = buildJsonArray {
                    add(sampleResponse)
                    add(buildJsonObject {
                        put("bookId", "BOOK-1")
                        put("totalVar", 40_000.0)
                        put("calculatedAt", "2026-03-23T10:00:00Z")
                    })
                }
                coEvery { riskClient.getFactorRiskHistory("BOOK-1", 100) } returns history

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/BOOK-1/factor-risk")

                    response.status shouldBe HttpStatusCode.OK

                    val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    arr.size shouldBe 2
                    arr[0].jsonObject["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                }
            }
        }
    }
})
