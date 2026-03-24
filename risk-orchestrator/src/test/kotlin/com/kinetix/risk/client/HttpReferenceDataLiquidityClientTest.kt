package com.kinetix.risk.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

class HttpReferenceDataLiquidityClientTest : FunSpec({

    fun mockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json() }
        }

    val sampleLiquidityJson = """{
        "instrumentId": "AAPL",
        "adv": 10000000.0,
        "bidAskSpreadBps": 5.0,
        "assetClass": "EQUITY",
        "advUpdatedAt": "2026-03-24T10:00:00Z",
        "advStale": false,
        "advStalenessDays": 0,
        "createdAt": "2026-03-24T10:00:00Z",
        "updatedAt": "2026-03-24T10:00:00Z"
    }"""

    test("getLiquidityData returns Success with InstrumentLiquidityDto for known instrument") {
        val httpClient = mockClient {
            respond(
                content = sampleLiquidityJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpReferenceDataServiceClient(httpClient, "http://localhost:8090")

        val result = client.getLiquidityData("AAPL")

        val success = result.shouldBeInstanceOf<ClientResponse.Success<com.kinetix.risk.client.dtos.InstrumentLiquidityDto>>()
        success.value.instrumentId shouldBe "AAPL"
        success.value.adv shouldBe 10_000_000.0
        success.value.bidAskSpreadBps shouldBe 5.0
        success.value.assetClass shouldBe "EQUITY"
        success.value.advStale shouldBe false
    }

    test("getLiquidityData returns NotFound when instrument has no liquidity record") {
        val httpClient = mockClient {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val client = HttpReferenceDataServiceClient(httpClient, "http://localhost:8090")

        val result = client.getLiquidityData("UNKNOWN")

        val notFound = result.shouldBeInstanceOf<ClientResponse.NotFound>()
        notFound.httpStatus shouldBe 404
    }

    test("getLiquidityData calls the correct endpoint URL") {
        var capturedPath = ""
        val httpClient = mockClient { request ->
            capturedPath = request.url.encodedPath
            respond(
                content = sampleLiquidityJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpReferenceDataServiceClient(httpClient, "http://localhost:8090")

        client.getLiquidityData("AAPL")

        capturedPath shouldBe "/api/v1/liquidity/AAPL"
    }

    test("getLiquidityDataBatch calls batch endpoint and returns list") {
        val batchJson = """[
            {"instrumentId":"AAPL","adv":10000000.0,"bidAskSpreadBps":5.0,"assetClass":"EQUITY","advUpdatedAt":"2026-03-24T10:00:00Z","advStale":false,"advStalenessDays":0,"createdAt":"2026-03-24T10:00:00Z","updatedAt":"2026-03-24T10:00:00Z"},
            {"instrumentId":"MSFT","adv":20000000.0,"bidAskSpreadBps":3.0,"assetClass":"EQUITY","advUpdatedAt":"2026-03-24T10:00:00Z","advStale":false,"advStalenessDays":0,"createdAt":"2026-03-24T10:00:00Z","updatedAt":"2026-03-24T10:00:00Z"}
        ]"""
        var capturedUrl = ""
        val httpClient = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                content = batchJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpReferenceDataServiceClient(httpClient, "http://localhost:8090")

        val result = client.getLiquidityDataBatch(listOf("AAPL", "MSFT"))

        result.size shouldBe 2
        result["AAPL"]!!.adv shouldBe 10_000_000.0
        result["MSFT"]!!.adv shouldBe 20_000_000.0
        capturedUrl shouldContain "/api/v1/liquidity/batch"
    }
})

private infix fun String.shouldContain(substring: String) {
    if (!this.contains(substring)) {
        throw AssertionError("Expected '$this' to contain '$substring'")
    }
}
