package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.GreekImpact
import com.kinetix.risk.model.HedgeConstraints
import com.kinetix.risk.model.HedgeRecommendation
import com.kinetix.risk.model.HedgeStatus
import com.kinetix.risk.model.HedgeSuggestion
import com.kinetix.risk.model.HedgeTarget
import com.kinetix.risk.service.HedgeRecommendationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

class HedgeRecommendationRoutesAcceptanceTest : FunSpec({

    val service = mockk<HedgeRecommendationService>()

    beforeEach { clearMocks(service) }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                hedgeRecommendationRoutes(service)
            }
            block()
        }
    }

    fun sampleGreekImpact() = GreekImpact(
        deltaBefore = 1000.0, deltaAfter = 50.0,
        gammaBefore = 200.0, gammaAfter = 210.0,
        vegaBefore = 500.0, vegaAfter = 480.0,
        thetaBefore = -30.0, thetaAfter = -31.5,
        rhoBefore = 10.0, rhoAfter = 9.8,
    )

    fun sampleSuggestion() = HedgeSuggestion(
        instrumentId = "AAPL-P-2026",
        instrumentType = "OPTION",
        side = "BUY",
        quantity = 1000.0,
        estimatedCost = 5_250.0,
        crossingCost = 250.0,
        carrycostPerDay = -5.0,
        targetReduction = 950.0,
        targetReductionPct = 0.95,
        residualMetric = 50.0,
        greekImpact = sampleGreekImpact(),
        liquidityTier = "TIER_1",
        dataQuality = "FRESH",
    )

    val sampleRecommendation = HedgeRecommendation(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        bookId = "BOOK-1",
        targetMetric = HedgeTarget.DELTA,
        targetReductionPct = 0.90,
        requestedAt = Instant.parse("2026-03-24T10:00:00Z"),
        status = HedgeStatus.PENDING,
        constraints = HedgeConstraints(
            maxNotional = 500_000.0,
            maxSuggestions = 5,
            respectPositionLimits = true,
            instrumentUniverse = null,
            allowedSides = null,
        ),
        suggestions = listOf(sampleSuggestion()),
        preHedgeGreeks = sampleGreekImpact(),
        sourceJobId = "job-123",
        acceptedBy = null,
        acceptedAt = null,
        expiresAt = Instant.parse("2026-03-24T10:30:00Z"),
    )

    test("POST /api/v1/risk/hedge-suggest/{bookId} returns 201 with recommendation") {
        coEvery { service.suggestHedge(BookId("BOOK-1"), HedgeTarget.DELTA, 0.90, any()) } returns sampleRecommendation

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetMetric":"DELTA","targetReductionPct":0.90}""")
            }

            response.status shouldBe HttpStatusCode.Created

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "00000000-0000-0000-0000-000000000001"
            body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
            body["targetMetric"]?.jsonPrimitive?.content shouldBe "DELTA"
            body["targetReductionPct"]?.jsonPrimitive?.double shouldBe 0.90
            body["status"]?.jsonPrimitive?.content shouldBe "PENDING"
            body["sourceJobId"]?.jsonPrimitive?.content shouldBe "job-123"
        }
    }

    test("POST hedge-suggest returns suggestions with full greek impact") {
        coEvery { service.suggestHedge(BookId("BOOK-1"), HedgeTarget.DELTA, 0.90, any()) } returns sampleRecommendation

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetMetric":"DELTA","targetReductionPct":0.90}""")
            }

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val suggestions = body["suggestions"]?.jsonArray
            suggestions?.size shouldBe 1

            val first = suggestions?.first()?.jsonObject
            first?.get("instrumentId")?.jsonPrimitive?.content shouldBe "AAPL-P-2026"
            first?.get("side")?.jsonPrimitive?.content shouldBe "BUY"
            first?.get("liquidityTier")?.jsonPrimitive?.content shouldBe "TIER_1"
            first?.get("dataQuality")?.jsonPrimitive?.content shouldBe "FRESH"

            val greekImpact = first?.get("greekImpact")?.jsonObject
            greekImpact?.get("deltaBefore")?.jsonPrimitive?.double shouldBe 1000.0
            greekImpact?.get("deltaAfter")?.jsonPrimitive?.double shouldBe 50.0
            // Gamma INCREASED — Lyapunov problem is visible
            greekImpact?.get("gammaAfter")?.jsonPrimitive?.double shouldBe 210.0
        }
    }

    test("POST hedge-suggest returns 400 for unknown target metric") {
        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetMetric":"UNKNOWN","targetReductionPct":0.90}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST hedge-suggest returns 400 when service throws IllegalArgumentException (bad targetReductionPct)") {
        coEvery { service.suggestHedge(any(), any(), any(), any()) } throws IllegalArgumentException("targetReductionPct must be between 0 and 1.0")

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetMetric":"DELTA","targetReductionPct":1.5}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST hedge-suggest returns 500 when service throws IllegalStateException (stale greeks)") {
        coEvery { service.suggestHedge(any(), any(), any(), any()) } throws IllegalStateException("Greeks are stale")

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetMetric":"DELTA","targetReductionPct":0.80}""")
            }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    test("GET /api/v1/risk/hedge-suggest/{bookId} returns list of recommendations") {
        coEvery { service.getLatestRecommendations(BookId("BOOK-1"), 10) } returns listOf(sampleRecommendation)

        testApp {
            val response = client.get("/api/v1/risk/hedge-suggest/BOOK-1")

            response.status shouldBe HttpStatusCode.OK

            val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            arr.size shouldBe 1
            arr[0].jsonObject["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
        }
    }

    test("GET /api/v1/risk/hedge-suggest/{bookId}/{id} returns specific recommendation") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { service.getRecommendation(id) } returns sampleRecommendation

        testApp {
            val response = client.get("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "00000000-0000-0000-0000-000000000001"
        }
    }

    test("GET /api/v1/risk/hedge-suggest/{bookId}/{id} returns 404 when not found") {
        val id = UUID.fromString("99999999-9999-9999-9999-999999999999")
        coEvery { service.getRecommendation(id) } returns null

        testApp {
            val response = client.get("/api/v1/risk/hedge-suggest/BOOK-1/99999999-9999-9999-9999-999999999999")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("accepts a pending recommendation and sets status to ACCEPTED") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val accepted = sampleRecommendation.copy(
            status = HedgeStatus.ACCEPTED,
            acceptedBy = "trader-1",
            acceptedAt = Instant.parse("2026-03-24T10:05:00Z"),
        )
        coEvery { service.acceptRecommendation(id, "trader-1", null) } returns accepted

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001/accept") {
                contentType(ContentType.Application.Json)
                setBody("""{"acceptedBy":"trader-1"}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "ACCEPTED"
            body["acceptedBy"]?.jsonPrimitive?.content shouldBe "trader-1"
        }
    }

    test("rejects acceptance of an expired recommendation with HTTP 409") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { service.acceptRecommendation(id, "trader-1", null) } throws
            IllegalStateException("Recommendation has expired and cannot be accepted")

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001/accept") {
                contentType(ContentType.Application.Json)
                setBody("""{"acceptedBy":"trader-1"}""")
            }

            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("rejects acceptance of an already-accepted recommendation with HTTP 409") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        coEvery { service.acceptRecommendation(id, "trader-1", null) } throws
            IllegalStateException("Recommendation is not PENDING (current status: ACCEPTED)")

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001/accept") {
                contentType(ContentType.Application.Json)
                setBody("""{"acceptedBy":"trader-1"}""")
            }

            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("accept with suggestion_indices passes only those indices to the service") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val accepted = sampleRecommendation.copy(
            status = HedgeStatus.ACCEPTED,
            acceptedBy = "trader-1",
            acceptedAt = Instant.parse("2026-03-24T10:05:00Z"),
        )
        coEvery { service.acceptRecommendation(id, "trader-1", listOf(0)) } returns accepted

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001/accept") {
                contentType(ContentType.Application.Json)
                setBody("""{"acceptedBy":"trader-1","suggestionIndices":[0]}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "ACCEPTED"
        }
    }

    test("rejects a pending recommendation and sets status to REJECTED") {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val rejected = sampleRecommendation.copy(status = HedgeStatus.REJECTED)
        coEvery { service.rejectRecommendation(id) } returns rejected

        testApp {
            val response = client.post("/api/v1/risk/hedge-suggest/BOOK-1/00000000-0000-0000-0000-000000000001/reject")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]?.jsonPrimitive?.content shouldBe "REJECTED"
        }
    }
})
