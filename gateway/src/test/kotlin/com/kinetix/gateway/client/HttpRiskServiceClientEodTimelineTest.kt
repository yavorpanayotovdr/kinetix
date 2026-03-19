package com.kinetix.gateway.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpRiskServiceClientEodTimelineTest : FunSpec({

    fun buildClient(mockEngine: MockEngine): HttpRiskServiceClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return HttpRiskServiceClient(httpClient, "http://localhost")
    }

    test("getEodTimeline deserializes a successful response with all fields") {
        val responseJson = """
            {
              "bookId": "port-1",
              "from": "2026-01-01",
              "to": "2026-01-31",
              "entries": [
                {
                  "valuationDate": "2026-01-01",
                  "jobId": "job-abc",
                  "varValue": 12345.67,
                  "expectedShortfall": 15432.1,
                  "pvValue": 1000000.0,
                  "delta": 0.85,
                  "gamma": 0.02,
                  "vega": 500.0,
                  "theta": -120.5,
                  "rho": 200.0,
                  "promotedAt": "2026-01-01T18:00:00Z",
                  "promotedBy": "risk-manager",
                  "varChange": 500.0,
                  "varChangePct": 4.23,
                  "esChange": 600.0,
                  "calculationType": "PARAMETRIC",
                  "confidenceLevel": 0.99
                }
              ]
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/risk/eod-timeline/port-1"
                    && request.method == HttpMethod.Get
                    && request.url.parameters["from"] == "2026-01-01"
                    && request.url.parameters["to"] == "2026-01-31" -> {
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        val sut = buildClient(mockEngine)

        val result = sut.getEodTimeline("port-1", "2026-01-01", "2026-01-31")

        result shouldBe EodTimelineSummary(
            bookId = "port-1",
            from = "2026-01-01",
            to = "2026-01-31",
            entries = listOf(
                EodTimelineEntryItem(
                    valuationDate = "2026-01-01",
                    jobId = "job-abc",
                    varValue = 12345.67,
                    expectedShortfall = 15432.1,
                    pvValue = 1000000.0,
                    delta = 0.85,
                    gamma = 0.02,
                    vega = 500.0,
                    theta = -120.5,
                    rho = 200.0,
                    promotedAt = "2026-01-01T18:00:00Z",
                    promotedBy = "risk-manager",
                    varChange = 500.0,
                    varChangePct = 4.23,
                    esChange = 600.0,
                    calculationType = "PARAMETRIC",
                    confidenceLevel = 0.99,
                )
            ),
        )
    }

    test("getEodTimeline returns null on 404") {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":"not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        val result = sut.getEodTimeline("unknown-portfolio", "2026-01-01", "2026-01-31")

        result.shouldBeNull()
    }

    test("getEodTimeline deserializes entries with null optional fields") {
        val responseJson = """
            {
              "bookId": "port-2",
              "from": "2026-02-01",
              "to": "2026-02-01",
              "entries": [
                {
                  "valuationDate": "2026-02-01",
                  "jobId": "job-xyz",
                  "varValue": null,
                  "expectedShortfall": null,
                  "pvValue": null,
                  "delta": null,
                  "gamma": null,
                  "vega": null,
                  "theta": null,
                  "rho": null,
                  "promotedAt": null,
                  "promotedBy": null,
                  "varChange": null,
                  "varChangePct": null,
                  "esChange": null,
                  "calculationType": null,
                  "confidenceLevel": null
                }
              ]
            }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        val result = sut.getEodTimeline("port-2", "2026-02-01", "2026-02-01")

        result?.bookId shouldBe "port-2"
        result?.entries?.size shouldBe 1
        val entry = result?.entries?.first()
        entry?.valuationDate shouldBe "2026-02-01"
        entry?.jobId shouldBe "job-xyz"
        entry?.varValue.shouldBeNull()
        entry?.promotedBy.shouldBeNull()
        entry?.varChange.shouldBeNull()
    }

    test("getEodTimeline returns empty entries list when no EOD snapshots exist") {
        val responseJson = """
            {
              "bookId": "port-3",
              "from": "2026-03-01",
              "to": "2026-03-07",
              "entries": []
            }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        val result = sut.getEodTimeline("port-3", "2026-03-01", "2026-03-07")

        result?.bookId shouldBe "port-3"
        result?.entries shouldBe emptyList()
    }

    test("getEodTimeline appends from and to as query parameters in the request URL") {
        var capturedFrom: String? = null
        var capturedTo: String? = null

        val mockEngine = MockEngine { request ->
            capturedFrom = request.url.parameters["from"]
            capturedTo = request.url.parameters["to"]
            respond(
                content = """{"bookId":"port-1","from":"2026-01-01","to":"2026-01-31","entries":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        sut.getEodTimeline("port-1", "2026-01-01", "2026-01-31")

        capturedFrom shouldBe "2026-01-01"
        capturedTo shouldBe "2026-01-31"
    }
})
