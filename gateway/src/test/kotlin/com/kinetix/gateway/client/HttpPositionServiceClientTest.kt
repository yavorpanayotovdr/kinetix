package com.kinetix.gateway.client

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class HttpPositionServiceClientTest : FunSpec({

    test("listPortfolios deserializes response correctly") {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/portfolios" && request.method == HttpMethod.Get -> {
                    respond(
                        content = """[{"portfolioId":"port-1"},{"portfolioId":"port-2"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = HttpPositionServiceClient(client, "http://localhost")

        val result = sut.listPortfolios()

        result.size shouldBe 2
        result[0].id shouldBe PortfolioId("port-1")
        result[1].id shouldBe PortfolioId("port-2")
    }

    test("getPositions maps DTOs to domain models") {
        val responseJson = """
            [
              {
                "portfolioId": "port-1",
                "instrumentId": "AAPL",
                "assetClass": "EQUITY",
                "quantity": "100",
                "averageCost": { "amount": "150.00", "currency": "USD" },
                "marketPrice": { "amount": "155.50", "currency": "USD" },
                "marketValue": { "amount": "15550.00", "currency": "USD" },
                "unrealizedPnl": { "amount": "550.00", "currency": "USD" }
              }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/portfolios/port-1/positions" && request.method == HttpMethod.Get -> {
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = HttpPositionServiceClient(client, "http://localhost")

        val result = sut.getPositions(PortfolioId("port-1"))

        result.size shouldBe 1
        val position = result[0]
        position.portfolioId shouldBe PortfolioId("port-1")
        position.instrumentId shouldBe InstrumentId("AAPL")
        position.assetClass shouldBe AssetClass.EQUITY
        position.quantity shouldBe BigDecimal("100")
        position.averageCost shouldBe Money(BigDecimal("150.00"), Currency.getInstance("USD"))
        position.marketPrice shouldBe Money(BigDecimal("155.50"), Currency.getInstance("USD"))
    }

    test("bookTrade sends correct request body and maps response") {
        val tradedAt = Instant.parse("2025-03-15T14:30:00Z")
        var capturedBody: String? = null

        val responseJson = """
            {
              "trade": {
                "tradeId": "trade-1",
                "portfolioId": "port-1",
                "instrumentId": "AAPL",
                "assetClass": "EQUITY",
                "side": "BUY",
                "quantity": "50",
                "price": { "amount": "152.00", "currency": "USD" },
                "tradedAt": "2025-03-15T14:30:00Z"
              },
              "position": {
                "portfolioId": "port-1",
                "instrumentId": "AAPL",
                "assetClass": "EQUITY",
                "quantity": "150",
                "averageCost": { "amount": "150.67", "currency": "USD" },
                "marketPrice": { "amount": "155.50", "currency": "USD" },
                "marketValue": { "amount": "23325.00", "currency": "USD" },
                "unrealizedPnl": { "amount": "724.50", "currency": "USD" }
              }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/portfolios/port-1/trades" && request.method == HttpMethod.Post -> {
                    capturedBody = String(request.body.toByteArray())
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unhandled ${request.url}")
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = HttpPositionServiceClient(client, "http://localhost")

        val command = BookTradeCommand(
            tradeId = TradeId("trade-1"),
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("50"),
            price = Money(BigDecimal("152.00"), Currency.getInstance("USD")),
            tradedAt = tradedAt,
        )

        val result = sut.bookTrade(command)

        // Verify request body
        val parsedRequest = Json.decodeFromString<BookTradeRequestDto>(capturedBody!!)
        parsedRequest.tradeId shouldBe "trade-1"
        parsedRequest.instrumentId shouldBe "AAPL"
        parsedRequest.assetClass shouldBe "EQUITY"
        parsedRequest.side shouldBe "BUY"
        parsedRequest.quantity shouldBe "50"
        parsedRequest.priceAmount shouldBe "152.00"
        parsedRequest.priceCurrency shouldBe "USD"
        parsedRequest.tradedAt shouldBe "2025-03-15T14:30:00Z"

        // Verify response mapping
        val trade = result.trade
        trade.tradeId shouldBe TradeId("trade-1")
        trade.portfolioId shouldBe PortfolioId("port-1")
        trade.instrumentId shouldBe InstrumentId("AAPL")
        trade.assetClass shouldBe AssetClass.EQUITY
        trade.side shouldBe Side.BUY
        trade.quantity shouldBe BigDecimal("50")
        trade.price shouldBe Money(BigDecimal("152.00"), Currency.getInstance("USD"))
        trade.tradedAt shouldBe tradedAt

        val position = result.position
        position.portfolioId shouldBe PortfolioId("port-1")
        position.instrumentId shouldBe InstrumentId("AAPL")
        position.assetClass shouldBe AssetClass.EQUITY
        position.quantity shouldBe BigDecimal("150")
        position.averageCost shouldBe Money(BigDecimal("150.67"), Currency.getInstance("USD"))
        position.marketPrice shouldBe Money(BigDecimal("155.50"), Currency.getInstance("USD"))
    }
})
