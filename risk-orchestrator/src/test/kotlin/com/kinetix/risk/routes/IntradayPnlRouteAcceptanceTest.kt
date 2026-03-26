package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.InstrumentPnlBreakdown
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
    vannaPnl: String = "0",
    volgaPnl: String = "0",
    charmPnl: String = "0",
    crossGammaPnl: String = "0",
    instrumentPnl: List<InstrumentPnlBreakdown> = emptyList(),
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
    vannaPnl = bd(vannaPnl),
    volgaPnl = bd(volgaPnl),
    charmPnl = bd(charmPnl),
    crossGammaPnl = bd(crossGammaPnl),
    unexplainedPnl = bd("125.00"),
    highWaterMark = bd(highWaterMark),
    correlationId = correlationId,
    instrumentPnl = instrumentPnl,
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

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns all attribution fields including cross-Greeks") {
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
            snap.vannaPnl shouldBe "0"
            snap.volgaPnl shouldBe "0"
            snap.charmPnl shouldBe "0"
            snap.crossGammaPnl shouldBe "0"
            snap.unexplainedPnl shouldBe "125.00"
            snap.highWaterMark shouldBe "1200.00"
            snap.correlationId shouldBe "corr-1"
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} surfaces non-zero cross-Greek P&L fields") {
        val t = Instant.parse("2026-03-24T10:00:00Z")
        coEvery { repository.findSeries(BOOK, any(), any()) } returns listOf(
            snapshot(
                snapshotAt = t,
                vannaPnl = "12.50",
                volgaPnl = "7.30",
                charmPnl = "-3.10",
                crossGammaPnl = "5.00",
            ),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T00:00:00Z&to=2026-03-24T23:59:59Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val snap = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText()).snapshots[0]
            snap.vannaPnl shouldBe "12.50"
            snap.volgaPnl shouldBe "7.30"
            snap.charmPnl shouldBe "-3.10"
            snap.crossGammaPnl shouldBe "5.00"
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} surfaces cross-Greek fields in per-instrument breakdown") {
        val t = Instant.parse("2026-03-24T11:00:00Z")
        val breakdown = InstrumentPnlBreakdown(
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            totalPnl = "500.00",
            deltaPnl = "420.00",
            gammaPnl = "30.00",
            vegaPnl = "0.00",
            thetaPnl = "-5.00",
            rhoPnl = "2.00",
            vannaPnl = "8.00",
            volgaPnl = "4.50",
            charmPnl = "-1.20",
            crossGammaPnl = "3.00",
            unexplainedPnl = "38.70",
        )
        coEvery { repository.findSeries(BOOK, any(), any()) } returns listOf(
            snapshot(snapshotAt = t, instrumentPnl = listOf(breakdown)),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T00:00:00Z&to=2026-03-24T23:59:59Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val snap = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText()).snapshots[0]
            snap.instrumentPnl shouldHaveSize 1
            val item = snap.instrumentPnl[0]
            item.instrumentId shouldBe "AAPL"
            item.vannaPnl shouldBe "8.00"
            item.volgaPnl shouldBe "4.50"
            item.charmPnl shouldBe "-1.20"
            item.crossGammaPnl shouldBe "3.00"
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

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns pnl_vs_sod as totalPnl minus sodTotalPnl") {
        val t = Instant.parse("2026-03-24T11:00:00Z")
        val snap = snapshot(snapshotAt = t, totalPnl = "1500.00").copy(sodTotalPnl = bd("300.00"))
        coEvery { repository.findSeries(BOOK, any(), any()) } returns listOf(snap)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayPnlRoutes(repository) }

            val response = client.get(
                "/api/v1/risk/pnl/intraday/book-1" +
                    "?from=2026-03-24T00:00:00Z&to=2026-03-24T23:59:59Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<IntradayPnlSeriesResponse>(response.bodyAsText())
            body.snapshots[0].pnlVsSod shouldBe "1200.00"
        }
    }

    test("GET /api/v1/risk/pnl/intraday/{bookId} returns pnl_vs_sod equal to totalPnl when no SOD baseline") {
        val t = Instant.parse("2026-03-24T11:00:00Z")
        coEvery { repository.findSeries(BOOK, any(), any()) } returns listOf(
            snapshot(snapshotAt = t, totalPnl = "800.00"),
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
            body.snapshots[0].pnlVsSod shouldBe "800.00"
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
