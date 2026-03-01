package com.kinetix.risk.client

import com.kinetix.common.model.*
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

class HttpPositionServiceClientTest : FunSpec({

    fun mockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json() }
        }

    test("should return positions for known portfolio") {
        val httpClient = mockClient {
            respond(
                content = """[
                    {
                        "portfolioId": "port-1",
                        "instrumentId": "AAPL",
                        "assetClass": "EQUITY",
                        "quantity": "100",
                        "averageCost": {"amount": "150.00", "currency": "USD"},
                        "marketPrice": {"amount": "170.00", "currency": "USD"},
                        "marketValue": {"amount": "17000.00", "currency": "USD"},
                        "unrealizedPnl": {"amount": "2000.00", "currency": "USD"},
                        "realizedPnl": {"amount": "500.00", "currency": "USD"}
                    }
                ]""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPositionServiceClient(httpClient, "http://localhost:8081")

        val result = client.getPositions(PortfolioId("port-1"))

        val success = result.shouldBeInstanceOf<ClientResponse.Success<List<Position>>>()
        success.value shouldHaveSize 1
        success.value[0].portfolioId shouldBe PortfolioId("port-1")
        success.value[0].instrumentId shouldBe InstrumentId("AAPL")
        success.value[0].assetClass shouldBe AssetClass.EQUITY
        success.value[0].quantity.compareTo(BigDecimal("100")) shouldBe 0
        success.value[0].averageCost.amount.compareTo(BigDecimal("150.00")) shouldBe 0
        success.value[0].marketPrice.amount.compareTo(BigDecimal("170.00")) shouldBe 0
        success.value[0].realizedPnl.amount.compareTo(BigDecimal("500.00")) shouldBe 0
    }

    test("should return empty list for unknown portfolio") {
        val httpClient = mockClient {
            respond(
                content = "[]",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPositionServiceClient(httpClient, "http://localhost:8081")

        val result = client.getPositions(PortfolioId("unknown-port"))

        val success = result.shouldBeInstanceOf<ClientResponse.Success<List<Position>>>()
        success.value.shouldBeEmpty()
    }

    test("should return all distinct portfolio IDs") {
        val httpClient = mockClient {
            respond(
                content = """[
                    {"portfolioId": "port-1"},
                    {"portfolioId": "port-2"},
                    {"portfolioId": "port-3"}
                ]""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpPositionServiceClient(httpClient, "http://localhost:8081")

        val result = client.getDistinctPortfolioIds()

        val success = result.shouldBeInstanceOf<ClientResponse.Success<List<PortfolioId>>>()
        success.value shouldHaveSize 3
        success.value[0] shouldBe PortfolioId("port-1")
        success.value[1] shouldBe PortfolioId("port-2")
        success.value[2] shouldBe PortfolioId("port-3")
    }
})
