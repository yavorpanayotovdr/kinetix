package com.kinetix.gateway.contract

import com.kinetix.common.model.*
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class GatewayPositionContractTest : BehaviorSpec({

    val positionClient = mockk<PositionServiceClient>()

    beforeEach { clearMocks(positionClient) }

    given("gateway routing to position-service") {

        `when`("POST /api/v1/portfolios/{portfolioId}/trades with valid body") {
            then("returns 201 with trade and position shape") {
                val trade = Trade(
                    tradeId = TradeId("t-1"),
                    portfolioId = PortfolioId("port-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    side = Side.BUY,
                    quantity = BigDecimal("100"),
                    price = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
                )
                val position = Position(
                    portfolioId = PortfolioId("port-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    quantity = BigDecimal("100"),
                    averageCost = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    marketPrice = Money(BigDecimal("155.00"), Currency.getInstance("USD")),
                )
                coEvery { positionClient.bookTrade(any()) } returns BookTradeResult(trade, position)

                testApplication {
                    application { module(positionClient) }
                    val response = client.post("/api/v1/portfolios/port-1/trades") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"tradeId":"t-1","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"100","priceAmount":"150.00","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}""")
                    }
                    response.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("trade") shouldBe true
                    body.containsKey("position") shouldBe true
                    val tradeObj = body["trade"]?.jsonObject
                    tradeObj?.containsKey("tradeId") shouldBe true
                    tradeObj?.containsKey("portfolioId") shouldBe true
                    tradeObj?.containsKey("instrumentId") shouldBe true
                    tradeObj?.containsKey("assetClass") shouldBe true
                }
            }
        }

        `when`("GET /api/v1/portfolios/{portfolioId}/positions") {
            then("returns 200 with position array shape") {
                val position = Position(
                    portfolioId = PortfolioId("port-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    quantity = BigDecimal("100"),
                    averageCost = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    marketPrice = Money(BigDecimal("155.00"), Currency.getInstance("USD")),
                )
                coEvery { positionClient.getPositions(PortfolioId("port-1")) } returns listOf(position)

                testApplication {
                    application { module(positionClient) }
                    val response = client.get("/api/v1/portfolios/port-1/positions")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    val pos = body[0].jsonObject
                    pos.containsKey("portfolioId") shouldBe true
                    pos.containsKey("instrumentId") shouldBe true
                    pos.containsKey("quantity") shouldBe true
                    pos.containsKey("marketValue") shouldBe true
                }
            }
        }

        `when`("POST /api/v1/portfolios/{portfolioId}/trades with invalid body") {
            then("returns 400 with error shape") {
                testApplication {
                    application { module(positionClient) }
                    val response = client.post("/api/v1/portfolios/port-1/trades") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"tradeId":"t-1","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"-100","priceAmount":"150.00","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("error") shouldBe true
                    body.containsKey("message") shouldBe true
                }
            }
        }
    }
})
