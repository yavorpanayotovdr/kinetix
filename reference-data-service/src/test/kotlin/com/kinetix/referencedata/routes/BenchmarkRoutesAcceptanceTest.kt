package com.kinetix.referencedata.routes

import com.kinetix.referencedata.model.Benchmark
import com.kinetix.referencedata.model.BenchmarkConstituent
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.BenchmarkRepository
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.service.BenchmarkService
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BenchmarkRoutesAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()
    val benchmarkRepo = mockk<BenchmarkRepository>()
    val benchmarkService = BenchmarkService(benchmarkRepo)

    val NOW = Instant.parse("2026-03-25T09:00:00Z")
    val TODAY = LocalDate.of(2026, 3, 25)

    fun sampleBenchmark(id: String = "SP500") = Benchmark(
        benchmarkId = id,
        name = "S&P 500",
        description = "Large-cap US equity index",
        createdAt = NOW,
    )

    fun sampleConstituents(benchmarkId: String = "SP500") = listOf(
        BenchmarkConstituent(benchmarkId, "AAPL", BigDecimal("0.0700"), TODAY),
        BenchmarkConstituent(benchmarkId, "MSFT", BigDecimal("0.0650"), TODAY),
        BenchmarkConstituent(benchmarkId, "NVDA", BigDecimal("0.0600"), TODAY),
    )

    test("GET /api/v1/benchmarks returns empty list when no benchmarks exist") {
        coEvery { benchmarkRepo.findAll() } returns emptyList()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.get("/api/v1/benchmarks")
            response.status shouldBe HttpStatusCode.OK
            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 0
        }
    }

    test("GET /api/v1/benchmarks returns list of benchmarks") {
        coEvery { benchmarkRepo.findAll() } returns listOf(sampleBenchmark())

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.get("/api/v1/benchmarks")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["benchmarkId"]?.jsonPrimitive?.content shouldBe "SP500"
            body[0].jsonObject["name"]?.jsonPrimitive?.content shouldBe "S&P 500"
            body[0].jsonObject["description"]?.jsonPrimitive?.content shouldBe "Large-cap US equity index"
        }
    }

    test("POST /api/v1/benchmarks creates a benchmark and returns 201") {
        val saved = slot<Benchmark>()
        coEvery { benchmarkRepo.save(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.post("/api/v1/benchmarks") {
                contentType(ContentType.Application.Json)
                setBody("""{"benchmarkId":"MSCIW","name":"MSCI World","description":"Global developed market index"}""")
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["benchmarkId"]?.jsonPrimitive?.content shouldBe "MSCIW"
            body["name"]?.jsonPrimitive?.content shouldBe "MSCI World"

            coVerify { benchmarkRepo.save(any()) }
            saved.captured.benchmarkId shouldBe "MSCIW"
            saved.captured.name shouldBe "MSCI World"
        }
    }

    test("POST /api/v1/benchmarks creates a benchmark without description") {
        val saved = slot<Benchmark>()
        coEvery { benchmarkRepo.save(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.post("/api/v1/benchmarks") {
                contentType(ContentType.Application.Json)
                setBody("""{"benchmarkId":"SPXTR","name":"S&P 500 Total Return"}""")
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["benchmarkId"]?.jsonPrimitive?.content shouldBe "SPXTR"
            saved.captured.description shouldBe null
        }
    }

    test("GET /api/v1/benchmarks/{id} returns 404 when benchmark does not exist") {
        coEvery { benchmarkRepo.findById("MISSING") } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.get("/api/v1/benchmarks/MISSING")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/benchmarks/{id} returns benchmark with constituents") {
        coEvery { benchmarkRepo.findById("SP500") } returns sampleBenchmark()
        coEvery { benchmarkRepo.findConstituents("SP500", TODAY) } returns sampleConstituents()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.get("/api/v1/benchmarks/SP500?asOfDate=2026-03-25")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["benchmarkId"]?.jsonPrimitive?.content shouldBe "SP500"
            body["name"]?.jsonPrimitive?.content shouldBe "S&P 500"

            val constituents = body["constituents"]?.jsonArray ?: error("missing constituents")
            constituents.size shouldBe 3
            constituents[0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            constituents[0].jsonObject["weight"]?.jsonPrimitive?.content shouldBe "0.0700"
        }
    }

    test("PUT /api/v1/benchmarks/{id}/constituents replaces constituent weights") {
        coEvery { benchmarkRepo.findById("SP500") } returns sampleBenchmark()
        coEvery { benchmarkRepo.replaceConstituents(any(), any()) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.put("/api/v1/benchmarks/SP500/constituents") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "asOfDate": "2026-03-25",
                        "constituents": [
                            {"instrumentId": "AAPL", "weight": "0.0700"},
                            {"instrumentId": "MSFT", "weight": "0.0650"}
                        ]
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.NoContent
            coVerify { benchmarkRepo.replaceConstituents("SP500", any()) }
        }
    }

    test("PUT /api/v1/benchmarks/{id}/constituents returns 404 when benchmark does not exist") {
        coEvery { benchmarkRepo.findById("MISSING") } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.put("/api/v1/benchmarks/MISSING/constituents") {
                contentType(ContentType.Application.Json)
                setBody("""{"asOfDate":"2026-03-25","constituents":[]}""")
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/benchmarks/{id}/returns records a daily return and returns 201") {
        coEvery { benchmarkRepo.findById("SP500") } returns sampleBenchmark()
        coEvery { benchmarkRepo.saveReturn(any()) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.post("/api/v1/benchmarks/SP500/returns") {
                contentType(ContentType.Application.Json)
                setBody("""{"returnDate":"2026-03-25","dailyReturn":"0.0125"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            coVerify { benchmarkRepo.saveReturn(any()) }
        }
    }

    test("POST /api/v1/benchmarks/{id}/returns returns 404 when benchmark does not exist") {
        coEvery { benchmarkRepo.findById("MISSING") } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.post("/api/v1/benchmarks/MISSING/returns") {
                contentType(ContentType.Application.Json)
                setBody("""{"returnDate":"2026-03-25","dailyReturn":"0.01"}""")
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/benchmarks/{id} with invalid asOfDate returns 400") {
        coEvery { benchmarkRepo.findById("SP500") } returns sampleBenchmark()

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, benchmarkService = benchmarkService) }

            val response = client.get("/api/v1/benchmarks/SP500?asOfDate=not-a-date")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
