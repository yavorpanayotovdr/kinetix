package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayPnlSnapshot
import com.kinetix.risk.model.PnlTrigger
import com.kinetix.risk.persistence.IntradayPnlRepository
import com.kinetix.risk.routes.dtos.IntradayPnlSnapshotDto
import com.kinetix.risk.routes.dtos.IntradayPnlSeriesResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant

private val BOOK = BookId("book-1")
private fun bd(v: String) = BigDecimal(v)

private fun snapshot(
    bookId: BookId = BOOK,
    snapshotAt: Instant,
    totalPnl: String = "1000.00",
    realisedPnl: String = "400.00",
    unrealisedPnl: String = "600.00",
    highWaterMark: String = "1200.00",
    trigger: PnlTrigger = PnlTrigger.POSITION_CHANGE,
    correlationId: String? = null,
): IntradayPnlSnapshot = IntradayPnlSnapshot(
    bookId = bookId,
    snapshotAt = snapshotAt,
    baseCurrency = "USD",
    trigger = trigger,
    totalPnl = bd(totalPnl),
    realisedPnl = bd(realisedPnl),
    unrealisedPnl = bd(unrealisedPnl),
    deltaPnl = bd("800.00"),
    gammaPnl = bd("50.00"),
    vegaPnl = bd("30.00"),
    thetaPnl = bd("-10.00"),
    rhoPnl = bd("5.00"),
    unexplainedPnl = bd("125.00"),
    highWaterMark = bd(highWaterMark),
    correlationId = correlationId,
)

class IntradayPnlRouteAcceptanceTest : FunSpec({

    val repository = mockk<IntradayPnlRepository>()

    beforeEach {
        clearMocks(repository)
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns series for given time range") {
        val t1 = Instant.parse("2026-03-24T09:00:00Z")
        val t2 = Instant.parse("2026-03-24T09:01:00Z")
        coEvery {
            repository.findSeries(
                BOOK,
                Instant.parse("2026-03-24T08:00:00Z"),
                Instant.parse("2026-03-24T10:00:00Z"),
            )
        } returns listOf(
            snapshot(snapshotAt = t1, totalPnl = "500.00", realisedPnl = "200.00", unrealisedPnl = "300.00"),
            snapshot(snapshotAt = t2, totalPnl = "1000.00", realisedPnl = "400.00", unrealisedPnl = "600.00"),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T08:00:00Z&to=2026-03-24T10:00:00Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText())
            body.bookId shouldBe "book-1"
            body.snapshots shouldHaveSize 2

            val first = body.snapshots[0]
            first.snapshotAt shouldBe "2026-03-24T09:00:00Z"
            first.totalPnl shouldBe "500.00"
            first.realisedPnl shouldBe "200.00"
            first.unrealisedPnl shouldBe "300.00"

            val second = body.snapshots[1]
            second.snapshotAt shouldBe "2026-03-24T09:01:00Z"
            second.totalPnl shouldBe "1000.00"
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns all attribution fields") {
        val t = Instant.parse("2026-03-24T09:30:00Z")
        coEvery { repository.findSeries(BOOK, any(), any()) } returns listOf(
            snapshot(snapshotAt = t, correlationId = "corr-1"),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T00:00:00Z&to=2026-03-24T23:59:59Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText())
            val snap = body.snapshots[0]
            snap.baseCurrency shouldBe "USD"
            snap.trigger shouldBe "position_change"
            snap.deltaPnl shouldBe "800.00"
            snap.gammaPnl shouldBe "50.00"
            snap.vegaPnl shouldBe "30.00"
            snap.thetaPnl shouldBe "-10.00"
            snap.rhoPnl shouldBe "5.00"
            snap.unexplainedPnl shouldBe "125.00"
            snap.highWaterMark shouldBe "1200.00"
            snap.correlationId shouldBe "corr-1"
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns empty list when no snapshots in range") {
        coEvery { repository.findSeries(BOOK, any(), any()) } returns emptyList()

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T00:00:00Z&to=2026-03-24T23:59:59Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText())
            body.bookId shouldBe "book-1"
            body.snapshots shouldHaveSize 0
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns 400 when from parameter is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1?to=2026-03-24T10:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns 400 when to parameter is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1?from=2026-03-24T08:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns 400 for invalid timestamp format") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1?from=not-a-date&to=2026-03-24T10:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
