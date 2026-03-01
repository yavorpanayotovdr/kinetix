package com.kinetix.gateway.routes

import com.kinetix.common.model.*
import com.kinetix.gateway.client.BookTradeCommand
import com.kinetix.gateway.client.BookTradeResult
import com.kinetix.gateway.client.PortfolioSummary
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private fun usd(amount: String) = Money(BigDecimal(amount), USD)

class PositionRoutesTest : FunSpec({

    val positionClient = mockk<PositionServiceClient>()

    beforeEach { clearMocks(positionClient) }

    // --- GET /api/v1/portfolios ---

    test("GET /api/v1/portfolios returns 200 with portfolio list") {
        coEvery { positionClient.listPortfolios() } returns listOf(
            PortfolioSummary(PortfolioId("port-1")),
            PortfolioSummary(PortfolioId("port-2")),
        )

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body[1].jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-2"
        }
    }

    test("GET /api/v1/portfolios returns empty array when no portfolios") {
        coEvery { positionClient.listPortfolios() } returns emptyList()

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    // --- POST /api/v1/portfolios/{portfolioId}/trades ---

    test("POST /trades returns 201 Created with trade and position") {
        val trade = Trade(
            tradeId = TradeId("t-1"),
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("100"),
            price = usd("150.00"),
            tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )
        val position = Position(
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("100"),
            averageCost = usd("150.00"),
            marketPrice = usd("155.00"),
        )
        coEvery { positionClient.bookTrade(any()) } returns BookTradeResult(trade, position)

        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "AAPL",
                        "assetClass": "EQUITY",
                        "side": "BUY",
                        "quantity": "100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["trade"]!!.jsonObject["tradeId"]?.jsonPrimitive?.content shouldBe "t-1"
            body["trade"]!!.jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["position"]!!.jsonObject["quantity"]?.jsonPrimitive?.content shouldBe "100"
        }
    }

    test("POST /trades passes portfolioId from path to command") {
        val commandSlot = slot<BookTradeCommand>()
        val trade = Trade(
            tradeId = TradeId("t-1"),
            portfolioId = PortfolioId("my-port"),
            instrumentId = InstrumentId("MSFT"),
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("50"),
            price = usd("300.00"),
            tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )
        val position = Position(
            portfolioId = PortfolioId("my-port"),
            instrumentId = InstrumentId("MSFT"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("50"),
            averageCost = usd("300.00"),
            marketPrice = usd("300.00"),
        )
        coEvery { positionClient.bookTrade(capture(commandSlot)) } returns BookTradeResult(trade, position)

        testApplication {
            application { module(positionClient) }
            client.post("/api/v1/portfolios/my-port/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "MSFT",
                        "assetClass": "EQUITY",
                        "side": "BUY",
                        "quantity": "50",
                        "priceAmount": "300.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            commandSlot.captured.portfolioId shouldBe PortfolioId("my-port")
        }
    }

    test("POST /trades returns 400 for invalid assetClass") {
        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "AAPL",
                        "assetClass": "INVALID",
                        "side": "BUY",
                        "quantity": "100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /trades returns 400 for negative quantity") {
        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "AAPL",
                        "assetClass": "EQUITY",
                        "side": "BUY",
                        "quantity": "-100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /trades returns 400 for blank tradeId") {
        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": " ",
                        "instrumentId": "AAPL",
                        "assetClass": "EQUITY",
                        "side": "BUY",
                        "quantity": "100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /trades response includes computed position fields") {
        val trade = Trade(
            tradeId = TradeId("t-1"),
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("100"),
            price = usd("150.00"),
            tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )
        val position = Position(
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("100"),
            averageCost = usd("150.00"),
            marketPrice = usd("155.00"),
        )
        coEvery { positionClient.bookTrade(any()) } returns BookTradeResult(trade, position)

        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "AAPL",
                        "assetClass": "EQUITY",
                        "side": "BUY",
                        "quantity": "100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            val posObj = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["position"]!!.jsonObject
            posObj["marketValue"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "15500.00"
            posObj["unrealizedPnl"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "500.00"
        }
    }

    // --- GET /api/v1/portfolios/{portfolioId}/trades ---

    test("GET /trades returns 200 with trade history") {
        val trades = listOf(
            Trade(
                tradeId = TradeId("t-1"),
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                side = Side.BUY,
                quantity = BigDecimal("100"),
                price = usd("150.00"),
                tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
            ),
            Trade(
                tradeId = TradeId("t-2"),
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                side = Side.SELL,
                quantity = BigDecimal("50"),
                price = usd("300.00"),
                tradedAt = Instant.parse("2025-01-15T11:00:00Z"),
            ),
        )
        coEvery { positionClient.getTradeHistory(PortfolioId("port-1")) } returns trades

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios/port-1/trades")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["tradeId"]?.jsonPrimitive?.content shouldBe "t-1"
            body[0].jsonObject["side"]?.jsonPrimitive?.content shouldBe "BUY"
            body[1].jsonObject["tradeId"]?.jsonPrimitive?.content shouldBe "t-2"
            body[1].jsonObject["side"]?.jsonPrimitive?.content shouldBe "SELL"
        }
    }

    test("GET /trades returns empty array for portfolio with no trades") {
        coEvery { positionClient.getTradeHistory(PortfolioId("port-1")) } returns emptyList()

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios/port-1/trades")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    // --- GET /api/v1/portfolios/{portfolioId}/positions ---

    test("GET /positions returns 200 with position list") {
        val positions = listOf(
            Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = usd("150.00"),
                marketPrice = usd("155.00"),
            ),
            Position(
                portfolioId = PortfolioId("port-1"),
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("50"),
                averageCost = usd("300.00"),
                marketPrice = usd("310.00"),
            ),
        )
        coEvery { positionClient.getPositions(PortfolioId("port-1")) } returns positions

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios/port-1/positions")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body[0].jsonObject["quantity"]?.jsonPrimitive?.content shouldBe "100"
            body[1].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "MSFT"
        }
    }

    test("GET /positions returns empty array for unknown portfolio") {
        coEvery { positionClient.getPositions(PortfolioId("unknown")) } returns emptyList()

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios/unknown/positions")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("GET /positions includes computed marketValue and unrealizedPnl") {
        val position = Position(
            portfolioId = PortfolioId("port-1"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("100"),
            averageCost = usd("50.00"),
            marketPrice = usd("55.00"),
        )
        coEvery { positionClient.getPositions(PortfolioId("port-1")) } returns listOf(position)

        testApplication {
            application { module(positionClient) }
            val response = client.get("/api/v1/portfolios/port-1/positions")
            val posObj = Json.parseToJsonElement(response.bodyAsText()).jsonArray[0].jsonObject
            posObj["marketValue"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "5500.00"
            posObj["unrealizedPnl"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "500.00"
            posObj["marketValue"]!!.jsonObject["currency"]?.jsonPrimitive?.content shouldBe "USD"
        }
    }

    // --- Error handling ---

    test("error response body contains error and message fields") {
        testApplication {
            application { module(positionClient) }
            val response = client.post("/api/v1/portfolios/port-1/trades") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "tradeId": "t-1",
                        "instrumentId": "AAPL",
                        "assetClass": "NOT_REAL",
                        "side": "BUY",
                        "quantity": "100",
                        "priceAmount": "150.00",
                        "priceCurrency": "USD",
                        "tradedAt": "2025-01-15T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("error") shouldBe true
            body.containsKey("message") shouldBe true
        }
    }

    // --- Regression ---

    test("GET /health still returns 200 when position routes are installed") {
        testApplication {
            application { module(positionClient) }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
