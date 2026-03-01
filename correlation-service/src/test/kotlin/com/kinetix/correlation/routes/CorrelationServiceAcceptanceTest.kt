package com.kinetix.correlation.routes

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.module
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import com.kinetix.correlation.service.CorrelationIngestionService
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
import java.time.Instant

/**
 * Contract tests verifying that the correlation-service HTTP response shape can be
 * consumed by the risk-orchestrator's HttpCorrelationServiceClient DTO.
 *
 * The orchestrator expects:
 *   CorrelationMatrixDto { labels:List<String>, values:List<Double>, windowDays:Int, asOfDate, method }
 */
class CorrelationServiceAcceptanceTest : FunSpec({

    val correlationRepo = mockk<CorrelationMatrixRepository>()
    val ingestionService = mockk<CorrelationIngestionService>()

    val AS_OF = Instant.parse("2026-01-15T12:00:00Z")

    test("correlation matrix response shape matches CorrelationMatrixDto consumed by risk-orchestrator") {
        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT", "GOOGL"),
            values = listOf(1.0, 0.72, 0.68, 0.72, 1.0, 0.75, 0.68, 0.75, 1.0),
            windowDays = 252,
            asOfDate = AS_OF,
            method = EstimationMethod.HISTORICAL,
        )
        coEvery { correlationRepo.findLatest(listOf("AAPL", "MSFT", "GOOGL"), 252) } returns matrix

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.get("/api/v1/correlations/latest?labels=AAPL,MSFT,GOOGL&window=252")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Fields the orchestrator's CorrelationMatrixDto requires
            val labels: JsonArray = body["labels"]!!.jsonArray
            labels.size shouldBe 3
            labels[0].jsonPrimitive.content shouldBe "AAPL"
            labels[1].jsonPrimitive.content shouldBe "MSFT"
            labels[2].jsonPrimitive.content shouldBe "GOOGL"

            val values: JsonArray = body["values"]!!.jsonArray
            values.size shouldBe 9
            values[0].jsonPrimitive.double shouldBe 1.0
            values[1].jsonPrimitive.double shouldBe 0.72

            body["windowDays"]?.jsonPrimitive?.int shouldBe 252
            body["asOfDate"]?.jsonPrimitive?.content shouldBe AS_OF.toString()
            body["method"]?.jsonPrimitive?.content shouldBe "HISTORICAL"
        }
    }

    test("correlation matrix response works for shrinkage estimation method") {
        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.60, 0.60, 1.0),
            windowDays = 60,
            asOfDate = AS_OF,
            method = EstimationMethod.SHRINKAGE,
        )
        coEvery { correlationRepo.findLatest(listOf("AAPL", "MSFT"), 60) } returns matrix

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.get("/api/v1/correlations/latest?labels=AAPL,MSFT&window=60")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["windowDays"]?.jsonPrimitive?.int shouldBe 60
            body["method"]?.jsonPrimitive?.content shouldBe "SHRINKAGE"

            val values: JsonArray = body["values"]!!.jsonArray
            values.size shouldBe 4
        }
    }

    test("correlation matrix endpoint returns 404 when no matrix exists for the given labels and window") {
        coEvery { correlationRepo.findLatest(listOf("UNKNOWN1", "UNKNOWN2"), 252) } returns null

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.get("/api/v1/correlations/latest?labels=UNKNOWN1,UNKNOWN2&window=252")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
