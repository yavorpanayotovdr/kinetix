package com.kinetix.gateway.auth

import com.kinetix.common.model.*
import com.kinetix.common.security.Role
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

class BookAccessAcceptanceTest : FunSpec({

    val positionClient = mockk<PositionServiceClient>()
    val jwtConfig = TestJwtHelper.testJwtConfig()

    val bookAccessService = InMemoryBookAccessService(
        traderBooks = mapOf("trader-1" to setOf("book-A")),
    )

    beforeEach { clearMocks(positionClient) }

    test("TRADER can access positions in their own book") {
        coEvery { positionClient.getPositions(BookId("book-A")) } returns listOf(
            Position(
                bookId = BookId("book-A"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), USD),
                marketPrice = Money(BigDecimal("155.00"), USD),
            ),
        )

        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, positionClient = positionClient, bookAccessService = bookAccessService) }
            val response = client.get("/api/v1/books/book-A/positions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("TRADER cannot access positions in a book not assigned to them (403)") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, positionClient = positionClient, bookAccessService = bookAccessService) }
            val response = client.get("/api/v1/books/book-B/positions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("RISK_MANAGER can access positions in any book") {
        coEvery { positionClient.getPositions(BookId("book-B")) } returns emptyList()

        val token = TestJwtHelper.generateToken(userId = "rm-1", roles = listOf(Role.RISK_MANAGER))

        testApplication {
            application { module(jwtConfig, positionClient = positionClient, bookAccessService = bookAccessService) }
            val response = client.get("/api/v1/books/book-B/positions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("TRADER cannot book a trade in a book not assigned to them (403)") {
        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, positionClient = positionClient, bookAccessService = bookAccessService) }
            val response = client.post("/api/v1/books/book-B/trades") {
                header(HttpHeaders.Authorization, "Bearer $token")
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
                        "tradedAt": "2026-01-01T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("TRADER can book a trade in their own book") {
        val trade = Trade(
            tradeId = TradeId("t-1"),
            bookId = BookId("book-A"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("100"),
            price = Money(BigDecimal("150.00"), USD),
            tradedAt = Instant.parse("2026-01-01T10:00:00Z"),
        )
        val position = Position(
            bookId = BookId("book-A"),
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            quantity = BigDecimal("100"),
            averageCost = Money(BigDecimal("150.00"), USD),
            marketPrice = Money(BigDecimal("155.00"), USD),
        )
        coEvery { positionClient.bookTrade(any()) } returns BookTradeResult(trade, position)

        val token = TestJwtHelper.generateToken(userId = "trader-1", roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, positionClient = positionClient, bookAccessService = bookAccessService) }
            val response = client.post("/api/v1/books/book-A/trades") {
                header(HttpHeaders.Authorization, "Bearer $token")
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
                        "tradedAt": "2026-01-01T10:00:00Z"
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }
})
