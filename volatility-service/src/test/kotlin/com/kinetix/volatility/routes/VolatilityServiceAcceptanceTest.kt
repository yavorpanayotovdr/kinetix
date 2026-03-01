package com.kinetix.volatility.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.module
import com.kinetix.volatility.persistence.VolSurfaceRepository
import com.kinetix.volatility.service.VolatilityIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

/**
 * Contract tests verifying that the volatility-service HTTP response shape can be
 * consumed by the risk-orchestrator's HttpVolatilityServiceClient DTO.
 *
 * The orchestrator expects:
 *   VolSurfaceDto { instrumentId, asOfDate, points:[{strike:Double, maturityDays:Int, impliedVol:Double}], source }
 */
class VolatilityServiceAcceptanceTest : FunSpec({

    val volSurfaceRepo = mockk<VolSurfaceRepository>()
    val ingestionService = mockk<VolatilityIngestionService>()

    val AS_OF = Instant.parse("2026-01-15T12:00:00Z")

    test("vol surface response shape matches VolSurfaceDto consumed by risk-orchestrator") {
        val surface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = AS_OF,
            points = listOf(
                VolPoint(BigDecimal("100"), 30, BigDecimal("0.2500")),
                VolPoint(BigDecimal("110"), 30, BigDecimal("0.2200")),
                VolPoint(BigDecimal("100"), 90, BigDecimal("0.2800")),
            ),
            source = VolatilitySource.BLOOMBERG,
        )
        coEvery { volSurfaceRepo.findLatest(InstrumentId("AAPL")) } returns surface

        testApplication {
            application { module(volSurfaceRepo, ingestionService) }

            val response = client.get("/api/v1/volatility/AAPL/surface/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Top-level fields the orchestrator's VolSurfaceDto requires
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body["asOfDate"]?.jsonPrimitive?.content shouldBe AS_OF.toString()
            body["source"]?.jsonPrimitive?.content shouldBe "BLOOMBERG"

            val points: JsonArray = body["points"]!!.jsonArray
            points.size shouldBe 3

            // Each point must have strike:Double, maturityDays:Int, impliedVol:Double
            // — these are the exact field names VolPointDto in the orchestrator declares
            val firstPoint: JsonObject = points[0].jsonObject
            firstPoint["strike"]?.jsonPrimitive?.double shouldBe 100.0
            firstPoint["maturityDays"]?.jsonPrimitive?.int shouldBe 30
            firstPoint["impliedVol"]?.jsonPrimitive?.double shouldBe 0.25
        }
    }

    test("vol surface response works for exchange-sourced surface") {
        val surface = VolSurface(
            instrumentId = InstrumentId("SPX"),
            asOf = AS_OF,
            points = listOf(
                VolPoint(BigDecimal("4500"), 21, BigDecimal("0.1800")),
            ),
            source = VolatilitySource.EXCHANGE,
        )
        coEvery { volSurfaceRepo.findLatest(InstrumentId("SPX")) } returns surface

        testApplication {
            application { module(volSurfaceRepo, ingestionService) }

            val response = client.get("/api/v1/volatility/SPX/surface/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["instrumentId"]?.jsonPrimitive?.content shouldBe "SPX"
            body["source"]?.jsonPrimitive?.content shouldBe "EXCHANGE"

            val points: JsonArray = body["points"]!!.jsonArray
            points.size shouldBe 1

            val point: JsonObject = points[0].jsonObject
            point["strike"]?.jsonPrimitive?.double shouldBe 4500.0
            point["maturityDays"]?.jsonPrimitive?.int shouldBe 21
            point["impliedVol"]?.jsonPrimitive?.double shouldBe 0.18
        }
    }

    test("vol surface endpoint returns 404 when no surface exists for the instrument") {
        coEvery { volSurfaceRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(volSurfaceRepo, ingestionService) }

            val response = client.get("/api/v1/volatility/UNKNOWN/surface/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
