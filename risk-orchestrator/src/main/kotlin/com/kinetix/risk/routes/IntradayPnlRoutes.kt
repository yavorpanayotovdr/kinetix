package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayPnlSnapshot
import com.kinetix.risk.persistence.IntradayPnlRepository
import com.kinetix.risk.routes.dtos.InstrumentPnlBreakdownDto
import com.kinetix.risk.routes.dtos.IntradayPnlSeriesResponse
import com.kinetix.risk.routes.dtos.IntradayPnlSnapshotDto
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.format.DateTimeParseException

fun Route.intradayPnlRoutes(
    intradayPnlRepository: IntradayPnlRepository,
) {
    get("/api/v1/risk/pnl/intraday/{bookId}", {
        summary = "Get intraday P&L series for a portfolio"
        tags = listOf("Intraday P&L")
        request {
            pathParameter<String>("bookId") { description = "Portfolio identifier" }
            queryParameter<String>("from") {
                description = "Series start (ISO-8601 instant, e.g. 2026-03-24T08:00:00Z)"
                required = true
            }
            queryParameter<String>("to") {
                description = "Series end (ISO-8601 instant, e.g. 2026-03-24T17:00:00Z)"
                required = true
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")

        val fromParam = call.request.queryParameters["from"]
        val toParam = call.request.queryParameters["to"]

        if (fromParam == null || toParam == null) {
            call.respond(HttpStatusCode.BadRequest, "Both 'from' and 'to' query parameters are required")
            return@get
        }

        val from = try {
            Instant.parse(fromParam)
        } catch (_: DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, "Invalid 'from' parameter: expected ISO-8601 instant")
            return@get
        }

        val to = try {
            Instant.parse(toParam)
        } catch (_: DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, "Invalid 'to' parameter: expected ISO-8601 instant")
            return@get
        }

        val snapshots = intradayPnlRepository.findSeries(BookId(bookId), from, to)
        call.respond(
            IntradayPnlSeriesResponse(
                bookId = bookId,
                snapshots = snapshots.map { it.toDto() },
            ),
        )
    }
}

private fun IntradayPnlSnapshot.toDto(): IntradayPnlSnapshotDto = IntradayPnlSnapshotDto(
    snapshotAt = snapshotAt.toString(),
    baseCurrency = baseCurrency,
    trigger = trigger.name.lowercase(),
    totalPnl = totalPnl.toPlainString(),
    realisedPnl = realisedPnl.toPlainString(),
    unrealisedPnl = unrealisedPnl.toPlainString(),
    deltaPnl = deltaPnl.toPlainString(),
    gammaPnl = gammaPnl.toPlainString(),
    vegaPnl = vegaPnl.toPlainString(),
    thetaPnl = thetaPnl.toPlainString(),
    rhoPnl = rhoPnl.toPlainString(),
    unexplainedPnl = unexplainedPnl.toPlainString(),
    highWaterMark = highWaterMark.toPlainString(),
    instrumentPnl = instrumentPnl.map { pos ->
        InstrumentPnlBreakdownDto(
            instrumentId = pos.instrumentId,
            assetClass = pos.assetClass,
            totalPnl = pos.totalPnl,
            deltaPnl = pos.deltaPnl,
            gammaPnl = pos.gammaPnl,
            vegaPnl = pos.vegaPnl,
            thetaPnl = pos.thetaPnl,
            rhoPnl = pos.rhoPnl,
            unexplainedPnl = pos.unexplainedPnl,
        )
    },
    correlationId = correlationId,
)
