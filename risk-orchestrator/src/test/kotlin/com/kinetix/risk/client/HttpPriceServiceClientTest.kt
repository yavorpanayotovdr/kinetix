package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PriceSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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

        result.shouldNotBeNull()
        result.instrumentId shouldBe InstrumentId("AAPL")
        result.price.amount.compareTo(BigDecimal("170.50")) shouldBe 0
        result.source shouldBe PriceSource.EXCHANGE
    }

    test("returns null when instrument not found") {
        val httpClient = mockClient {
            respond(content = "", status = HttpStatusCode.NotFound)
        }
        val client = HttpPriceServiceClient(httpClient, "http://localhost:8082")

        val result = client.getLatestPrice(InstrumentId("UNKNOWN"))

        result.shouldBeNull()
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

        result shouldHaveSize 2
        result[0].price.amount.compareTo(BigDecimal("168.00")) shouldBe 0
        result[1].price.amount.compareTo(BigDecimal("170.50")) shouldBe 0
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

        result.shouldBeEmpty()
    }
})
