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

class GatewayKrdContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    val sampleResponse = buildJsonObject {
        put("bookId", "BOOK-1")
        putJsonArray("instruments") {
            addJsonObject {
                put("instrumentId", "UST-10Y")
                put("totalDv01", "854.02")
                putJsonArray("krdBuckets") {
                    addJsonObject {
                        put("tenorLabel", "2Y")
                        put("tenorDays", 730)
                        put("dv01", "45.12")
                    }
                    addJsonObject {
                        put("tenorLabel", "5Y")
                        put("tenorDays", 1825)
                        put("dv01", "120.34")
                    }
                    addJsonObject {
                        put("tenorLabel", "10Y")
                        put("tenorDays", 3650)
                        put("dv01", "680.55")
                    }
                    addJsonObject {
                        put("tenorLabel", "30Y")
                        put("tenorDays", 10950)
                        put("dv01", "8.01")
                    }
                }
            }
        }
        putJsonArray("aggregated") {
            addJsonObject {
                put("tenorLabel", "2Y")
                put("tenorDays", 730)
                put("dv01", "45.12")
            }
            addJsonObject {
                put("tenorLabel", "10Y")
                put("tenorDays", 3650)
                put("dv01", "680.55")
            }
        }
    }

    given("gateway routing to KRD endpoint") {

        `when`("GET /api/v1/risk/krd/{bookId} with a book that has fixed-income positions") {
            then("returns 200 with KRD result") {
                coEvery { riskClient.getKeyRateDurations("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/krd/BOOK-1")

                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }

        `when`("GET /api/v1/risk/krd/{bookId} response body contains bookId") {
            then("bookId matches requested book") {
                coEvery { riskClient.getKeyRateDurations("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/krd/BOOK-1")
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                    body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
                }
            }
        }

        `when`("GET /api/v1/risk/krd/{bookId} response body contains instruments") {
            then("instruments array is present") {
                coEvery { riskClient.getKeyRateDurations("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/krd/BOOK-1")
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                    body.containsKey("instruments") shouldBe true
                    body["instruments"]!!.jsonArray.size shouldBe 1
                }
            }
        }

        `when`("GET /api/v1/risk/krd/{bookId} response body contains aggregated buckets") {
            then("aggregated array is present") {
                coEvery { riskClient.getKeyRateDurations("BOOK-1") } returns sampleResponse

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/krd/BOOK-1")
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                    body.containsKey("aggregated") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/risk/krd/{bookId} when upstream returns null") {
            then("returns 404") {
                coEvery { riskClient.getKeyRateDurations("UNKNOWN") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/krd/UNKNOWN")

                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
})
