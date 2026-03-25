package com.kinetix.gateway.contract

import com.kinetix.gateway.client.VolatilityServiceClient
import com.kinetix.gateway.dto.VolPointDiffResponse
import com.kinetix.gateway.dto.VolPointResponse
import com.kinetix.gateway.dto.VolSurfaceDiffResponse
import com.kinetix.gateway.dto.VolSurfaceResponse
import com.kinetix.gateway.moduleWithVolSurface
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GatewayVolSurfaceContractAcceptanceTest : BehaviorSpec({

    val volClient = mockk<VolatilityServiceClient>()

    beforeEach { clearMocks(volClient) }

    given("gateway routing to volatility-service surface endpoint") {

        `when`("GET /api/v1/volatility/{instrumentId}/surface returns a surface") {
            then("responds 200 with instrumentId, asOfDate, source, and points array") {
                coEvery { volClient.getSurface("AAPL") } returns VolSurfaceResponse(
                    instrumentId = "AAPL",
                    asOfDate = "2026-03-25T10:00:00Z",
                    source = "BLOOMBERG",
                    points = listOf(
                        VolPointResponse(100.0, 30, 0.25),
                        VolPointResponse(110.0, 30, 0.22),
                    ),
                )

                testApplication {
                    application { moduleWithVolSurface(volClient) }
                    val response = client.get("/api/v1/volatility/AAPL/surface")
                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
                    body["asOfDate"]?.jsonPrimitive?.content shouldBe "2026-03-25T10:00:00Z"
                    body["source"]?.jsonPrimitive?.content shouldBe "BLOOMBERG"

                    val points = body["points"]!!.jsonArray
                    points.size shouldBe 2
                    val first = points[0].jsonObject
                    first["strike"]?.jsonPrimitive?.double shouldBe 100.0
                    first["maturityDays"]?.jsonPrimitive?.int shouldBe 30
                    first["impliedVol"]?.jsonPrimitive?.double shouldBe 0.25
                }
            }
        }

        `when`("GET /api/v1/volatility/{instrumentId}/surface when no surface exists") {
            then("responds 404") {
                coEvery { volClient.getSurface("UNKNOWN") } returns null

                testApplication {
                    application { moduleWithVolSurface(volClient) }
                    val response = client.get("/api/v1/volatility/UNKNOWN/surface")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/volatility/{instrumentId}/surface/diff returns diffs") {
            then("responds 200 with instrumentId, baseDate, compareDate, and diffs array") {
                coEvery { volClient.getSurfaceDiff("AAPL", "2026-03-24T10:00:00Z") } returns VolSurfaceDiffResponse(
                    instrumentId = "AAPL",
                    baseDate = "2026-03-25T10:00:00Z",
                    compareDate = "2026-03-24T10:00:00Z",
                    diffs = listOf(
                        VolPointDiffResponse(100.0, 30, 0.25, 0.24, 0.01),
                    ),
                )

                testApplication {
                    application { moduleWithVolSurface(volClient) }
                    val response = client.get("/api/v1/volatility/AAPL/surface/diff?compareDate=2026-03-24T10:00:00Z")
                    response.status shouldBe HttpStatusCode.OK

                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
                    body.containsKey("baseDate") shouldBe true
                    body.containsKey("compareDate") shouldBe true

                    val diffs = body["diffs"]!!.jsonArray
                    diffs.size shouldBe 1
                    val d = diffs[0].jsonObject
                    d["strike"]?.jsonPrimitive?.double shouldBe 100.0
                    d["maturityDays"]?.jsonPrimitive?.int shouldBe 30
                    d.containsKey("baseVol") shouldBe true
                    d.containsKey("compareVol") shouldBe true
                    d.containsKey("diff") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/volatility/{instrumentId}/surface/diff without compareDate") {
            then("responds 400") {
                testApplication {
                    application { moduleWithVolSurface(volClient) }
                    val response = client.get("/api/v1/volatility/AAPL/surface/diff")
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }
})
