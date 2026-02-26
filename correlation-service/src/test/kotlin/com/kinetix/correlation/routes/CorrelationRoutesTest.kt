package com.kinetix.correlation.routes

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.module
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import com.kinetix.correlation.service.CorrelationIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.mockk.just
import io.mockk.runs
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant

private val NOW = Instant.parse("2026-02-24T10:00:00Z")

class CorrelationRoutesTest : FunSpec({

    val correlationRepo = mockk<CorrelationMatrixRepository>()
    val ingestionService = mockk<CorrelationIngestionService>()

    beforeEach {
        clearMocks(correlationRepo)
    }

    test("GET latest returns 200 with matrix") {
        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.65, 0.65, 1.0),
            windowDays = 252,
            asOfDate = NOW,
            method = EstimationMethod.HISTORICAL,
        )
        coEvery { correlationRepo.findLatest(listOf("AAPL", "MSFT"), 252) } returns matrix

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.get("/api/v1/correlations/latest?labels=AAPL,MSFT&window=252")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "AAPL"
            body shouldContain "MSFT"
            body shouldContain "0.65"
            body shouldContain "HISTORICAL"
        }
    }

    test("GET latest returns 404 for unknown labels") {
        coEvery { correlationRepo.findLatest(listOf("UNKNOWN1", "UNKNOWN2"), 252) } returns null

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.get("/api/v1/correlations/latest?labels=UNKNOWN1,UNKNOWN2&window=252")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST ingest returns 201 Created") {
        coEvery { ingestionService.ingest(any()) } just runs

        testApplication {
            application { module(correlationRepo, ingestionService) }

            val response = client.post("/api/v1/correlations/ingest") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "labels": ["AAPL", "MSFT"],
                        "values": [1.0, 0.65, 0.65, 1.0],
                        "windowDays": 252,
                        "method": "HISTORICAL"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldContain "AAPL"
        }
    }
})
