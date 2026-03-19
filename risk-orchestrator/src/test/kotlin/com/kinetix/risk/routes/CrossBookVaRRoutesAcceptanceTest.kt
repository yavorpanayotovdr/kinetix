package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.InMemoryCrossBookVaRCache
import com.kinetix.risk.model.*
import com.kinetix.risk.routes.dtos.CrossBookVaRResultResponse
import com.kinetix.risk.service.CrossBookVaRCalculationService
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

private val TEST_INSTANT = Instant.parse("2025-06-01T12:00:00Z")

private val json = Json { ignoreUnknownKeys = true }

private fun crossBookResult(
    portfolioGroupId: String = "desk-1",
    bookIds: List<String> = listOf("book-A", "book-B"),
    varValue: Double = 15000.0,
    expectedShortfall: Double = 18750.0,
) = CrossBookValuationResult(
    portfolioGroupId = portfolioGroupId,
    bookIds = bookIds.map { BookId(it) },
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    componentBreakdown = listOf(
        ComponentBreakdown(AssetClass.EQUITY, varValue * 0.6, 60.0),
        ComponentBreakdown(AssetClass.FIXED_INCOME, varValue * 0.4, 40.0),
    ),
    bookContributions = listOf(
        BookVaRContribution(
            bookId = BookId("book-A"),
            varContribution = 9000.0,
            percentageOfTotal = 60.0,
            standaloneVar = 10000.0,
            diversificationBenefit = 1000.0,
            marginalVar = 0.000123,
        ),
        BookVaRContribution(
            bookId = BookId("book-B"),
            varContribution = 6000.0,
            percentageOfTotal = 40.0,
            standaloneVar = 8000.0,
            diversificationBenefit = 2000.0,
            marginalVar = 0.000456,
        ),
    ),
    totalStandaloneVar = 18000.0,
    diversificationBenefit = 3000.0,
    calculatedAt = TEST_INSTANT,
)

class CrossBookVaRRoutesAcceptanceTest : FunSpec({

    val service = mockk<CrossBookVaRCalculationService>()
    val cache = InMemoryCrossBookVaRCache()

    beforeEach {
        clearMocks(service)
    }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(json) }
            routing { crossBookVaRRoutes(service, cache) }
            block()
        }
    }

    test("POST /api/v1/risk/var/cross-book returns 200 with correct response shape") {
        val result = crossBookResult()
        coEvery { service.calculate(any(), any()) } returns result

        testApp {
            val response = client.post("/api/v1/risk/var/cross-book") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":["book-A","book-B"],"portfolioGroupId":"desk-1"}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val body = json.decodeFromString<CrossBookVaRResultResponse>(response.bodyAsText())
            body.portfolioGroupId shouldBe "desk-1"
            body.bookIds shouldBe listOf("book-A", "book-B")
            body.calculationType shouldBe "PARAMETRIC"
            body.confidenceLevel shouldBe "CL_95"
            body.varValue shouldBe "15000.00"
            body.expectedShortfall shouldBe "18750.00"
            body.totalStandaloneVar shouldBe "18000.00"
            body.diversificationBenefit shouldBe "3000.00"
            body.calculatedAt shouldBe TEST_INSTANT.toString()
            body.bookContributions.size shouldBe 2
            body.componentBreakdown.size shouldBe 2
        }
    }

    test("POST /api/v1/risk/var/cross-book with empty bookIds returns 400") {
        testApp {
            val response = client.post("/api/v1/risk/var/cross-book") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":[],"portfolioGroupId":"desk-1"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/risk/var/cross-book returns 404 when service returns null") {
        coEvery { service.calculate(any(), any()) } returns null

        testApp {
            val response = client.post("/api/v1/risk/var/cross-book") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":["book-X"],"portfolioGroupId":"desk-2"}""")
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/risk/var/cross-book/{groupId} returns cached result") {
        val result = crossBookResult(portfolioGroupId = "desk-cached", varValue = 12000.0)
        cache.put("desk-cached", result)

        testApp {
            val response = client.get("/api/v1/risk/var/cross-book/desk-cached")

            response.status shouldBe HttpStatusCode.OK

            val body = json.decodeFromString<CrossBookVaRResultResponse>(response.bodyAsText())
            body.portfolioGroupId shouldBe "desk-cached"
            body.varValue shouldBe "12000.00"
            body.calculatedAt shouldBe TEST_INSTANT.toString()
        }
    }

    test("GET /api/v1/risk/var/cross-book/{groupId} returns 404 when not cached") {
        testApp {
            val response = client.get("/api/v1/risk/var/cross-book/non-existent-group")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("response includes bookContributions with marginalVar field") {
        val result = crossBookResult()
        coEvery { service.calculate(any(), any()) } returns result

        testApp {
            val response = client.post("/api/v1/risk/var/cross-book") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookIds":["book-A","book-B"],"portfolioGroupId":"desk-1"}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val raw = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val contributions = raw["bookContributions"]!!.jsonArray
            contributions.size shouldBe 2
            contributions[0].jsonObject["marginalVar"]!!.jsonPrimitive.content shouldBe "0.000123"
            contributions[1].jsonObject["marginalVar"]!!.jsonPrimitive.content shouldBe "0.000456"
        }
    }
})
