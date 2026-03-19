package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.dtos.InstrumentDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class HttpInstrumentServiceClientTest : FunSpec({

    fun mockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json() }
        }

    val sampleInstrumentJson = """{
        "instrumentId": "AAPL",
        "instrumentType": "CASH_EQUITY",
        "displayName": "Apple Inc.",
        "assetClass": "EQUITY",
        "currency": "USD",
        "attributes": {},
        "createdAt": "2026-01-01T00:00:00Z",
        "updatedAt": "2026-01-01T00:00:00Z"
    }"""

    test("getInstrument returns InstrumentDto for valid ID") {
        val httpClient = mockClient {
            respond(
                content = sampleInstrumentJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpInstrumentServiceClient(httpClient, "http://localhost:8089")

        val result = client.getInstrument(InstrumentId("AAPL"))

        val success = result.shouldBeInstanceOf<ClientResponse.Success<InstrumentDto>>()
        success.value.instrumentId shouldBe "AAPL"
        success.value.instrumentType shouldBe "CASH_EQUITY"
        success.value.displayName shouldBe "Apple Inc."
    }

    test("getInstrument returns NotFound for 404") {
        val httpClient = mockClient {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val client = HttpInstrumentServiceClient(httpClient, "http://localhost:8089")

        val result = client.getInstrument(InstrumentId("UNKNOWN"))

        val notFound = result.shouldBeInstanceOf<ClientResponse.NotFound>()
        notFound.httpStatus shouldBe 404
    }

    test("getInstruments batch-fetches and caches") {
        var requestCount = 0
        val httpClient = mockClient { request ->
            requestCount++
            val id = request.url.encodedPath.substringAfterLast("/")
            respond(
                content = """{"instrumentId":"$id","instrumentType":"CASH_EQUITY","displayName":"$id","assetClass":"EQUITY","currency":"USD","attributes":{},"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpInstrumentServiceClient(httpClient, "http://localhost:8089")

        val result = client.getInstruments(listOf(InstrumentId("AAPL"), InstrumentId("MSFT")))
        result.size shouldBe 2
        result["AAPL"]!!.instrumentId shouldBe "AAPL"
        result["MSFT"]!!.instrumentId shouldBe "MSFT"
        requestCount shouldBe 2

        // Second call should use cache — no new HTTP requests
        val result2 = client.getInstruments(listOf(InstrumentId("AAPL")))
        result2.size shouldBe 1
        requestCount shouldBe 2
    }
})
