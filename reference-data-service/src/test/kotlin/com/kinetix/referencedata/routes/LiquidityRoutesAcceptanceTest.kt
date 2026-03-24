package com.kinetix.referencedata.routes

import com.kinetix.referencedata.model.InstrumentLiquidity
import com.kinetix.referencedata.model.InstrumentLiquidityTier
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.persistence.InstrumentLiquidityRepository
import com.kinetix.referencedata.service.InstrumentLiquidityService
import com.kinetix.referencedata.service.ReferenceDataIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class LiquidityRoutesAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()
    val liquidityRepo = mockk<InstrumentLiquidityRepository>()
    val liquidityService = InstrumentLiquidityService(liquidityRepo)

    val NOW = Instant.parse("2026-03-24T10:00:00Z")
    val STALE = Instant.parse("2026-03-21T10:00:00Z")  // 3 days ago

    fun sampleLiquidity(instrumentId: String = "AAPL", adv: Double = 10_000_000.0) = InstrumentLiquidity(
        instrumentId = instrumentId,
        adv = adv,
        bidAskSpreadBps = 5.0,
        assetClass = "EQUITY",
        liquidityTier = InstrumentLiquidityTier.TIER_2,
        advUpdatedAt = NOW,
        createdAt = NOW,
        updatedAt = NOW,
    )

    test("GET /api/v1/liquidity/{id} returns liquidity data for a known instrument") {
        coEvery { liquidityRepo.findById("AAPL") } returns sampleLiquidity()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.get("/api/v1/liquidity/AAPL")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body["adv"]?.jsonPrimitive?.content?.toDouble() shouldBe 10_000_000.0
            body["bidAskSpreadBps"]?.jsonPrimitive?.content?.toDouble() shouldBe 5.0
            body["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            body["advStale"]?.jsonPrimitive?.content?.toBoolean() shouldBe false
        }
    }

    test("GET /api/v1/liquidity/{id} returns 404 when instrument not found") {
        coEvery { liquidityRepo.findById("UNKNOWN") } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.get("/api/v1/liquidity/UNKNOWN")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/liquidity/{id} marks advStale true when ADV older than 2 days") {
        val stale = sampleLiquidity().copy(advUpdatedAt = STALE)
        coEvery { liquidityRepo.findById("AAPL") } returns stale

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.get("/api/v1/liquidity/AAPL")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["advStale"]?.jsonPrimitive?.content?.toBoolean() shouldBe true
            body["advStalenessDays"]?.jsonPrimitive?.content?.toInt()!! >= 2
        }
    }

    test("POST /api/v1/liquidity creates or updates a liquidity record and returns 201") {
        val saved = slot<InstrumentLiquidity>()
        coEvery { liquidityRepo.upsert(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.post("/api/v1/liquidity") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "instrumentId": "MSFT",
                        "adv": 25000000.0,
                        "bidAskSpreadBps": 3.5,
                        "assetClass": "EQUITY"
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "MSFT"
            body["adv"]?.jsonPrimitive?.content?.toDouble() shouldBe 25_000_000.0

            coVerify { liquidityRepo.upsert(any()) }
            saved.captured.instrumentId shouldBe "MSFT"
            saved.captured.adv shouldBe 25_000_000.0
        }
    }

    test("GET /api/v1/liquidity/{id} includes liquidityTier in response") {
        val tier2Sample = sampleLiquidity().copy(adv = 25_000_000.0) // adv=25M, spread=5bps → TIER_2
        coEvery { liquidityRepo.findById("AAPL") } returns tier2Sample

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.get("/api/v1/liquidity/AAPL")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["liquidityTier"]?.jsonPrimitive?.content shouldBe "TIER_2"
        }
    }

    test("POST /api/v1/liquidity classifies TIER_1 for high ADV and tight spread") {
        val saved = slot<InstrumentLiquidity>()
        coEvery { liquidityRepo.upsert(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.post("/api/v1/liquidity") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "instrumentId": "SPY",
                        "adv": 80000000.0,
                        "bidAskSpreadBps": 1.0,
                        "assetClass": "EQUITY"
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["liquidityTier"]?.jsonPrimitive?.content shouldBe "TIER_1"
            saved.captured.liquidityTier.name shouldBe "TIER_1"
        }
    }

    test("POST /api/v1/liquidity classifies ILLIQUID for low ADV") {
        val saved = slot<InstrumentLiquidity>()
        coEvery { liquidityRepo.upsert(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.post("/api/v1/liquidity") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "instrumentId": "ILLIQ-1",
                        "adv": 100000.0,
                        "bidAskSpreadBps": 200.0,
                        "assetClass": "FIXED_INCOME"
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["liquidityTier"]?.jsonPrimitive?.content shouldBe "ILLIQUID"
            saved.captured.liquidityTier.name shouldBe "ILLIQUID"
        }
    }

    test("GET /api/v1/liquidity/batch returns liquidity for multiple instruments") {
        coEvery { liquidityRepo.findByIds(listOf("AAPL", "MSFT")) } returns listOf(
            sampleLiquidity("AAPL"),
            sampleLiquidity("MSFT", adv = 25_000_000.0),
        )

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, liquidityService = liquidityService) }

            val response = client.get("/api/v1/liquidity/batch?ids=AAPL,MSFT")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
        }
    }
})
