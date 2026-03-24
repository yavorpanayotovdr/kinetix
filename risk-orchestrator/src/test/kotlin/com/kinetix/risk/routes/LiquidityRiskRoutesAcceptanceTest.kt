package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.common.model.LiquidityRiskResult
import com.kinetix.common.model.LiquidityTier
import com.kinetix.common.model.PositionLiquidityRisk
import com.kinetix.risk.persistence.LiquidityRiskSnapshotRepository
import com.kinetix.risk.service.LiquidityRiskService
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LiquidityRiskRoutesAcceptanceTest : FunSpec({

    val liquidityRiskService = mockk<LiquidityRiskService>()
    val snapshotRepository = mockk<LiquidityRiskSnapshotRepository>()

    beforeEach {
        clearMocks(liquidityRiskService, snapshotRepository)
    }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                liquidityRiskRoutes(
                    liquidityRiskService = liquidityRiskService,
                    snapshotRepository = snapshotRepository,
                )
            }
            block()
        }
    }

    val sampleResult = LiquidityRiskResult(
        bookId = "BOOK-1",
        portfolioLvar = 316_227.76,
        dataCompleteness = 0.85,
        portfolioConcentrationStatus = "OK",
        calculatedAt = "2026-03-24T10:00:00Z",
        positionRisks = listOf(
            PositionLiquidityRisk(
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                marketValue = 17_000.0,
                tier = LiquidityTier.HIGH_LIQUID,
                horizonDays = 1,
                adv = 10_000_000.0,
                advMissing = false,
                advStale = false,
                lvarContribution = 316_227.76,
                stressedLiquidationValue = 16_500.0,
                concentrationStatus = "OK",
            )
        ),
    )

    test("POST /api/v1/books/{bookId}/liquidity-risk triggers calculation and returns 200") {
        coEvery { liquidityRiskService.calculateAndSave(BookId("BOOK-1"), 50_000.0) } returns sampleResult

        testApp {
            val response = client.post("/api/v1/books/BOOK-1/liquidity-risk") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseVar": 50000.0}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
            body["portfolioLvar"]?.jsonPrimitive?.double shouldBe 316_227.76
            body["dataCompleteness"]?.jsonPrimitive?.double shouldBe 0.85
            body["portfolioConcentrationStatus"]?.jsonPrimitive?.content shouldBe "OK"
            body["calculatedAt"]?.jsonPrimitive?.content shouldBe "2026-03-24T10:00:00Z"

            val positions = body["positionRisks"]?.jsonArray
            positions?.size shouldBe 1
            val pos = positions?.first()?.jsonObject
            pos?.get("instrumentId")?.jsonPrimitive?.content shouldBe "AAPL"
            pos?.get("tier")?.jsonPrimitive?.content shouldBe "HIGH_LIQUID"
            pos?.get("horizonDays")?.jsonPrimitive?.int shouldBe 1
            pos?.get("advMissing")?.jsonPrimitive?.content shouldBe "false"
        }
    }

    test("POST /api/v1/books/{bookId}/liquidity-risk returns 204 when book has no positions") {
        coEvery { liquidityRiskService.calculateAndSave(BookId("EMPTY-BOOK"), 50_000.0) } returns null

        testApp {
            val response = client.post("/api/v1/books/EMPTY-BOOK/liquidity-risk") {
                contentType(ContentType.Application.Json)
                setBody("""{"baseVar": 50000.0}""")
            }

            response.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("GET /api/v1/books/{bookId}/liquidity-risk/latest returns the most recent snapshot") {
        coEvery { snapshotRepository.findLatestByBookId("BOOK-1") } returns sampleResult

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/liquidity-risk/latest")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "BOOK-1"
            body["portfolioLvar"]?.jsonPrimitive?.double shouldBe 316_227.76
        }
    }

    test("GET /api/v1/books/{bookId}/liquidity-risk/latest returns 404 when no snapshot exists") {
        coEvery { snapshotRepository.findLatestByBookId("UNKNOWN") } returns null

        testApp {
            val response = client.get("/api/v1/books/UNKNOWN/liquidity-risk/latest")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/books/{bookId}/liquidity-risk returns history in descending order") {
        val history = listOf(
            sampleResult.copy(portfolioLvar = 300_000.0, calculatedAt = "2026-03-24T10:00:00Z"),
            sampleResult.copy(portfolioLvar = 200_000.0, calculatedAt = "2026-03-23T10:00:00Z"),
        )
        coEvery { snapshotRepository.findAllByBookId("BOOK-1", any()) } returns history

        testApp {
            val response = client.get("/api/v1/books/BOOK-1/liquidity-risk")

            response.status shouldBe HttpStatusCode.OK

            val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            arr.size shouldBe 2
            arr[0].jsonObject["portfolioLvar"]?.jsonPrimitive?.double shouldBe 300_000.0
            arr[1].jsonObject["portfolioLvar"]?.jsonPrimitive?.double shouldBe 200_000.0
        }
    }
})
