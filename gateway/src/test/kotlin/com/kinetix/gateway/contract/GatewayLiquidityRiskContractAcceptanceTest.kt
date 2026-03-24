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

class GatewayLiquidityRiskContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    val sampleResponse = buildJsonObject {
        put("bookId", "BOOK-1")
        put("portfolioLvar", 316227.76)
        put("dataCompleteness", 0.85)
        put("portfolioConcentrationStatus", "OK")
        put("calculatedAt", "2026-03-24T10:00:00Z")
        putJsonArray("positionRisks") {
            addJsonObject {
                put("instrumentId", "AAPL")
                put("tier", "HIGH_LIQUID")
                put("horizonDays", 1)
                put("advMissing", false)
            }
        }
    }

    given("gateway routing to liquidity risk endpoints") {

        `when`("POST /api/v1/books/{bookId}/liquidity-risk with valid baseVar") {
            then("returns 200 with liquidity risk response from upstream") {
                coEvery { riskClient.calculateLiquidityRisk("BOOK-1", 50000.0) } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/books/BOOK-1/liquidity-risk") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"baseVar": 50000.0}""")
                    }

                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                    body.containsKey("portfolioLvar") shouldBe true
                    body.containsKey("dataCompleteness") shouldBe true
                    body.containsKey("portfolioConcentrationStatus") shouldBe true
                    body.containsKey("positionRisks") shouldBe true
                }
            }
        }

        `when`("POST /api/v1/books/{bookId}/liquidity-risk when book has no positions") {
            then("returns 204 No Content") {
                coEvery { riskClient.calculateLiquidityRisk("EMPTY-BOOK", any()) } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.post("/api/v1/books/EMPTY-BOOK/liquidity-risk") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"baseVar": 50000.0}""")
                    }

                    response.status shouldBe HttpStatusCode.NoContent
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/liquidity-risk/latest with existing snapshot") {
            then("returns 200 with the latest snapshot") {
                coEvery { riskClient.getLatestLiquidityRisk("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/BOOK-1/liquidity-risk/latest")

                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                    body.containsKey("portfolioLvar") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/liquidity-risk/latest when no snapshot exists") {
            then("returns 404") {
                coEvery { riskClient.getLatestLiquidityRisk("UNKNOWN") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/UNKNOWN/liquidity-risk/latest")

                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/liquidity-risk returns history") {
            then("returns 200 with array of snapshots") {
                val history = buildJsonArray {
                    add(sampleResponse)
                    add(sampleResponse.toMutableMap().also { it["portfolioLvar"] = JsonPrimitive(200_000.0) }.let { buildJsonObject { for ((k, v) in it) put(k, v) } })
                }
                coEvery { riskClient.getLiquidityRiskHistory("BOOK-1", 100) } returns history

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/books/BOOK-1/liquidity-risk")

                    response.status shouldBe HttpStatusCode.OK

                    val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    arr.size shouldBe 2
                    arr[0].jsonObject["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                }
            }
        }
    }
})
