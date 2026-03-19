package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

private val sampleComparisonResult = buildJsonObject {
    put("bookId", "port-1")
    put("baseJobId", "job-abc")
    put("targetJobId", "job-xyz")
    put("varDelta", "-1500.00")
    put("esDelta", "-1875.00")
}

private val sampleDayOverDayResult = buildJsonObject {
    put("bookId", "port-1")
    put("targetDate", "2026-03-13")
    put("baseDate", "2026-03-12")
    put("varDelta", "2000.00")
}

private val sampleAttributionResult = buildJsonObject {
    put("bookId", "port-1")
    put("targetDate", "2026-03-13")
    put("baseDate", "2026-03-12")
    put("deltaEffect", "1200.00")
    put("vegaEffect", "500.00")
}

private val sampleModelComparisonResult = buildJsonObject {
    put("bookId", "port-1")
    put("baseModel", "PARAMETRIC")
    put("targetModel", "MONTE_CARLO")
    put("varDelta", "-800.00")
}

class RunComparisonRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    // --- POST /api/v1/risk/compare/{bookId} ---

    test("POST /api/v1/risk/compare/{bookId} returns comparison result for valid job IDs") {
        coEvery { riskClient.compareRuns("port-1", "job-abc", "job-xyz") } returns sampleComparisonResult

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/compare/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseJobId":"job-abc","targetJobId":"job-xyz"}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["baseJobId"]?.jsonPrimitive?.content shouldBe "job-abc"
            body["targetJobId"]?.jsonPrimitive?.content shouldBe "job-xyz"
            body["varDelta"]?.jsonPrimitive?.content shouldBe "-1500.00"
        }
    }

    test("POST /api/v1/risk/compare/{bookId} returns 400 when baseJobId is missing") {
        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/compare/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetJobId":"job-xyz"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("error") shouldBe true
        }
    }

    test("POST /api/v1/risk/compare/{bookId} returns 400 when targetJobId is missing") {
        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/compare/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseJobId":"job-abc"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("error") shouldBe true
        }
    }

    test("POST /api/v1/risk/compare/{bookId} forwards bookId correctly") {
        coEvery { riskClient.compareRuns("my-portfolio", "job-1", "job-2") } returns sampleComparisonResult

        testApplication {
            application { module(riskClient) }
            client.post("/api/v1/risk/compare/my-portfolio") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseJobId":"job-1","targetJobId":"job-2"}""")
            }
            coVerify { riskClient.compareRuns("my-portfolio", "job-1", "job-2") }
        }
    }

    // --- GET /api/v1/risk/compare/{bookId}/day-over-day ---

    test("GET /api/v1/risk/compare/{bookId}/day-over-day returns day-over-day result") {
        coEvery { riskClient.compareDayOverDay("port-1", "2026-03-13", "2026-03-12") } returns sampleDayOverDayResult

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/compare/port-1/day-over-day?targetDate=2026-03-13&baseDate=2026-03-12")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["varDelta"]?.jsonPrimitive?.content shouldBe "2000.00"
        }
    }

    test("GET /api/v1/risk/compare/{bookId}/day-over-day returns 404 when no data found") {
        coEvery { riskClient.compareDayOverDay("port-1", null, null) } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/compare/port-1/day-over-day")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/risk/compare/{bookId}/day-over-day passes null dates when not provided") {
        coEvery { riskClient.compareDayOverDay("port-1", null, null) } returns sampleDayOverDayResult

        testApplication {
            application { module(riskClient) }
            client.get("/api/v1/risk/compare/port-1/day-over-day")
            coVerify { riskClient.compareDayOverDay("port-1", null, null) }
        }
    }

    // --- POST /api/v1/risk/compare/{bookId}/day-over-day/attribution ---

    test("POST /api/v1/risk/compare/{bookId}/day-over-day/attribution returns attribution result") {
        coEvery {
            riskClient.compareDayOverDayAttribution("port-1", "2026-03-13", "2026-03-12")
        } returns sampleAttributionResult

        testApplication {
            application { module(riskClient) }
            val response = client.post(
                "/api/v1/risk/compare/port-1/day-over-day/attribution?targetDate=2026-03-13&baseDate=2026-03-12"
            ) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["deltaEffect"]?.jsonPrimitive?.content shouldBe "1200.00"
            body["vegaEffect"]?.jsonPrimitive?.content shouldBe "500.00"
        }
    }

    test("POST /api/v1/risk/compare/{bookId}/day-over-day/attribution passes null dates when absent") {
        coEvery {
            riskClient.compareDayOverDayAttribution("port-1", null, null)
        } returns sampleAttributionResult

        testApplication {
            application { module(riskClient) }
            client.post("/api/v1/risk/compare/port-1/day-over-day/attribution") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            coVerify { riskClient.compareDayOverDayAttribution("port-1", null, null) }
        }
    }

    // --- POST /api/v1/risk/compare/{bookId}/model ---

    test("POST /api/v1/risk/compare/{bookId}/model returns model comparison result") {
        coEvery { riskClient.compareModel("port-1", any()) } returns sampleModelComparisonResult

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/compare/port-1/model") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseModel":"PARAMETRIC","targetModel":"MONTE_CARLO"}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["varDelta"]?.jsonPrimitive?.content shouldBe "-800.00"
        }
    }

    test("POST /api/v1/risk/compare/{bookId}/model forwards request body to client") {
        val bodySlot = slot<JsonObject>()
        coEvery { riskClient.compareModel("port-1", capture(bodySlot)) } returns sampleModelComparisonResult

        testApplication {
            application { module(riskClient) }
            client.post("/api/v1/risk/compare/port-1/model") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseModel":"PARAMETRIC","targetModel":"MONTE_CARLO","confidenceLevel":"CL_99"}""")
            }
            bodySlot.captured["baseModel"]?.jsonPrimitive?.content shouldBe "PARAMETRIC"
            bodySlot.captured["targetModel"]?.jsonPrimitive?.content shouldBe "MONTE_CARLO"
            bodySlot.captured["confidenceLevel"]?.jsonPrimitive?.content shouldBe "CL_99"
        }
    }

    test("POST /api/v1/risk/compare/{bookId}/model propagates upstream errors") {
        coEvery { riskClient.compareModel(any(), any()) } throws
            com.kinetix.gateway.client.UpstreamErrorException(502, "risk-orchestrator unavailable")

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/compare/port-1/model") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseModel":"PARAMETRIC","targetModel":"MONTE_CARLO"}""")
            }
            response.status shouldBe HttpStatusCode.BadGateway
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["message"]?.jsonPrimitive?.content shouldBe "risk-orchestrator unavailable"
        }
    }
})
