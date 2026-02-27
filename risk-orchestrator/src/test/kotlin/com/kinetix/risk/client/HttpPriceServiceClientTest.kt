package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.math.BigDecimal
import java.time.Instant

class HttpPriceServiceClientTest : FunSpec({

    fun mockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json() }
        }

    test("returns latest price for known instrument") {
        val httpClient = mockClient {
            respond(
                content = """{"instrumentId":"AAPL","price":{"amount":"170.50","currency":"USD"},"timestamp":"2026-02-24T10:00:00Z","source":"EXCHANGE"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getLatestPrice(InstrumentId("AAPL"))

        val success = result.shouldBeInstanceOf<ClientResponse.Success<PricePoint>>()
        success.value.instrumentId shouldBe InstrumentId("AAPL")
        success.value.price.amount.compareTo(BigDecimal("170.50")) shouldBe 0
        success.value.source shouldBe PriceSource.EXCHANGE
    }

    test("returns NotFound when instrument not found") {
        val httpClient = mockClient {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getLatestPrice(InstrumentId("UNKNOWN"))

        val notFound = result.shouldBeInstanceOf<ClientResponse.NotFound>()
        notFound.httpStatus shouldBe 404
    }

    test("returns price history for known instrument") {
        val httpClient = mockClient {
            respond(
                content = """[
                    {"instrumentId":"AAPL","price":{"amount":"168.00","currency":"USD"},"timestamp":"2026-02-22T10:00:00Z","source":"EXCHANGE"},
                    {"instrumentId":"AAPL","price":{"amount":"170.50","currency":"USD"},"timestamp":"2026-02-23T10:00:00Z","source":"EXCHANGE"}
                ]""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getPriceHistory(
            InstrumentId("AAPL"),
            Instant.parse("2026-02-22T00:00:00Z"),
            Instant.parse("2026-02-24T00:00:00Z"),
        )

        val success = result.shouldBeInstanceOf<ClientResponse.Success<List<PricePoint>>>()
        success.value shouldHaveSize 2
        success.value[0].price.amount.compareTo(BigDecimal("168.00")) shouldBe 0
        success.value[1].price.amount.compareTo(BigDecimal("170.50")) shouldBe 0
    }

    test("returns empty list when no history available") {
        val httpClient = mockClient {
            respond(
                content = "[]",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getPriceHistory(
            InstrumentId("AAPL"),
            Instant.parse("2026-02-22T00:00:00Z"),
            Instant.parse("2026-02-24T00:00:00Z"),
        )

        val success = result.shouldBeInstanceOf<ClientResponse.Success<List<PricePoint>>>()
        success.value.shouldBeEmpty()
    }

    test("returns NotFound for price history when not found") {
        val httpClient = mockClient {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getPriceHistory(
            InstrumentId("UNKNOWN"),
            Instant.parse("2026-02-22T00:00:00Z"),
            Instant.parse("2026-02-24T00:00:00Z"),
        )

        val notFound = result.shouldBeInstanceOf<ClientResponse.NotFound>()
        notFound.httpStatus shouldBe 404
    }
})
