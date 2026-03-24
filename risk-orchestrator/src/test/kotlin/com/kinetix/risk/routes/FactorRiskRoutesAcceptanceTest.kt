package com.kinetix.risk.routes

import com.kinetix.risk.model.FactorContribution
import com.kinetix.risk.model.FactorDecompositionSnapshot
import com.kinetix.risk.persistence.FactorDecompositionRepository
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class FactorRiskRoutesAcceptanceTest : FunSpec({

    val repository = mockk<FactorDecompositionRepository>()

    beforeEach {
        clearMocks(repository)
    }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                factorRiskRoutes(repository)
            }
            block()
        }
    }

    val sampleSnapshot = FactorDecompositionSnapshot(
        bookId = "BOOK-1",
        calculatedAt = Instant.parse("2026-03-24T10:00:00Z"),
        totalVar = 50_000.0,
        systematicVar = 38_000.0,
        idiosyncraticVar = 12_000.0,
        rSquared = 0.576,
        concentrationWarning = false,
        factors = listOf(
            FactorContribution(
                factorType = "EQUITY_BETA",
                varContribution = 30_000.0,
                pctOfTotal = 0.60,
                loading = 1.2,
                loadingMethod = "OLS_REGRESSION",
            ),
            FactorContribution(
                factorType = "RATES_DURATION",
                varContribution = 8_000.0,
                pctOfTotal = 0.16,
                loading = -0.5,
                loadingMethod = "ANALYTICAL",
            ),
        ),
    )

    test("GET /api/v1/books/{bookId}/factor-risk/latest returns the most recent snapshot") {
        coEvery { repository.findLatestByBookId("BOOK-1") } returns sampleSnapshot

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/factor-risk/latest")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
            body["totalVar"]?.jsonPrimitive?.double shouldBe 50_000.0
            body["systematicVar"]?.jsonPrimitive?.double shouldBe 38_000.0
            body["idiosyncraticVar"]?.jsonPrimitive?.double shouldBe 12_000.0
            body["rSquared"]?.jsonPrimitive?.double shouldBe 0.576
            body["concentrationWarning"]?.jsonPrimitive?.boolean shouldBe false
            body["calculatedAt"]?.jsonPrimitive?.content shouldBe "2026-03-24T10:00:00Z"
        }
    }

    test("GET /api/v1/books/{bookId}/factor-risk/latest returns 404 when no snapshot exists") {
        coEvery { repository.findLatestByBookId("UNKNOWN") } returns null

        testApp {
            val response = client.get("/api/v1/books/UNKNOWN/factor-risk/latest")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/books/{bookId}/factor-risk/latest includes factor contributions") {
        coEvery { repository.findLatestByBookId("BOOK-1") } returns sampleSnapshot

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/factor-risk/latest")

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val factors = body["factors"]?.jsonArray!!
            factors.size shouldBe 2

            val equityFactor = factors.first { it.jsonObject["factorType"]?.jsonPrimitive?.content == "EQUITY_BETA" }.jsonObject
            equityFactor["varContribution"]?.jsonPrimitive?.double shouldBe 30_000.0
            equityFactor["pctOfTotal"]?.jsonPrimitive?.double shouldBe 0.60
            equityFactor["loadingMethod"]?.jsonPrimitive?.content shouldBe "OLS_REGRESSION"
        }
    }

    test("GET /api/v1/books/{bookId}/factor-risk returns history in descending order") {
        val history = listOf(
            sampleSnapshot.copy(totalVar = 60_000.0, calculatedAt = Instant.parse("2026-03-24T10:00:00Z")),
            sampleSnapshot.copy(totalVar = 50_000.0, calculatedAt = Instant.parse("2026-03-23T10:00:00Z")),
        )
        coEvery { repository.findAllByBookId("BOOK-1", any()) } returns history

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/factor-risk")

            response.status shouldBe HttpStatusCode.OK

            val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            arr.size shouldBe 2
            arr[0].jsonObject["totalVar"]?.jsonPrimitive?.double shouldBe 60_000.0
            arr[1].jsonObject["totalVar"]?.jsonPrimitive?.double shouldBe 50_000.0
        }
    }

    test("GET /api/v1/books/{bookId}/factor-risk respects the limit query parameter") {
        coEvery { repository.findAllByBookId("BOOK-1", 5) } returns listOf(sampleSnapshot)

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/factor-risk?limit=5")

            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /api/v1/books/{bookId}/factor-risk returns an empty array when no snapshots exist") {
        coEvery { repository.findAllByBookId("EMPTY-BOOK", any()) } returns emptyList()

        testApp {
            val response = client.get("/api/v1/books/EMPTY-BOOK/factor-risk")

            response.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(response.bodyAsText()).jsonArray.size shouldBe 0
        }
    }
})
