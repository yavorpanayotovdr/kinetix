package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRPoint
import com.kinetix.risk.model.TradeAnnotation
import com.kinetix.risk.routes.dtos.IntradayVaRTimelineResponse
import com.kinetix.risk.service.IntradayVaRTimelineService
import com.kinetix.risk.model.IntradayVaRTimelineResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.time.Instant

private val BOOK = BookId("book-1")

private fun varPoint(ts: String, varValue: Double = 100_000.0) = IntradayVaRPoint(
    timestamp = Instant.parse(ts),
    varValue = varValue,
    expectedShortfall = varValue * 1.2,
    delta = 0.5,
    gamma = 0.01,
    vega = 200.0,
)

private fun tradeAnnotation(ts: String, tradeId: String = "t-1") = TradeAnnotation(
    timestamp = Instant.parse(ts),
    instrumentId = "AAPL",
    side = "BUY",
    quantity = "100",
    tradeId = tradeId,
)

class IntradayVaRTimelineAcceptanceTest : FunSpec({

    val service = mockk<IntradayVaRTimelineService>()

    beforeEach { clearMocks(service) }

    test("GET /api/v1/risk/var/{bookId}/intraday returns timeline with VaR points and trade annotations") {
        val from = Instant.parse("2026-03-25T08:00:00Z")
        val to = Instant.parse("2026-03-25T16:00:00Z")

        coEvery { service.getTimeline(BOOK, from, to) } returns IntradayVaRTimelineResult(
            varPoints = listOf(
                varPoint("2026-03-25T09:00:00Z", varValue = 100_000.0),
                varPoint("2026-03-25T10:00:00Z", varValue = 110_000.0),
            ),
            tradeAnnotations = listOf(
                tradeAnnotation("2026-03-25T09:30:00Z", tradeId = "t-1"),
            ),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday" +
                    "?from=2026-03-25T08:00:00Z&to=2026-03-25T16:00:00Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayVaRTimelineResponse>(response.bodyAsText())
            body.bookId shouldBe "book-1"
            body.varPoints shouldHaveSize 2
            body.varPoints[0].timestamp shouldBe "2026-03-25T09:00:00Z"
            body.varPoints[0].varValue shouldBe 100_000.0
            body.varPoints[1].varValue shouldBe 110_000.0
            body.tradeAnnotations shouldHaveSize 1
            body.tradeAnnotations[0].tradeId shouldBe "t-1"
            body.tradeAnnotations[0].instrumentId shouldBe "AAPL"
            body.tradeAnnotations[0].side shouldBe "BUY"
            body.tradeAnnotations[0].quantity shouldBe "100"
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns all VaR point fields") {
        val from = Instant.parse("2026-03-25T08:00:00Z")
        val to = Instant.parse("2026-03-25T16:00:00Z")

        coEvery { service.getTimeline(BOOK, from, to) } returns IntradayVaRTimelineResult(
            varPoints = listOf(
                IntradayVaRPoint(
                    timestamp = Instant.parse("2026-03-25T09:00:00Z"),
                    varValue = 123_456.78,
                    expectedShortfall = 145_000.0,
                    delta = 0.72,
                    gamma = 0.03,
                    vega = 350.0,
                ),
            ),
            tradeAnnotations = emptyList(),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday" +
                    "?from=2026-03-25T08:00:00Z&to=2026-03-25T16:00:00Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayVaRTimelineResponse>(response.bodyAsText())
            val point = body.varPoints[0]
            point.expectedShortfall shouldBe 145_000.0
            point.delta shouldBe 0.72
            point.gamma shouldBe 0.03
            point.vega shouldBe 350.0
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns empty response when no data") {
        coEvery { service.getTimeline(BOOK, any(), any()) } returns IntradayVaRTimelineResult(
            varPoints = emptyList(),
            tradeAnnotations = emptyList(),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday" +
                    "?from=2026-03-25T08:00:00Z&to=2026-03-25T16:00:00Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayVaRTimelineResponse>(response.bodyAsText())
            body.varPoints shouldHaveSize 0
            body.tradeAnnotations shouldHaveSize 0
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 400 when from is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get("/api/v1/risk/var/book-1/intraday?to=2026-03-25T16:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 400 when to is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get("/api/v1/risk/var/book-1/intraday?from=2026-03-25T08:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 400 for invalid timestamp") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineRoutes(service) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday?from=not-a-date&to=2026-03-25T16:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
