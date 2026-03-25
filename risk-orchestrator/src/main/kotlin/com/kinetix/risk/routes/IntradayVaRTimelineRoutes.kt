package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRPoint
import com.kinetix.risk.model.TradeAnnotation
import com.kinetix.risk.routes.dtos.IntradayVaRPointDto
import com.kinetix.risk.routes.dtos.IntradayVaRTimelineResponse
import com.kinetix.risk.routes.dtos.TradeAnnotationDto
import com.kinetix.risk.service.IntradayVaRTimelineService
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.format.DateTimeParseException

fun Route.intradayVaRTimelineRoutes(service: IntradayVaRTimelineService) {
    get("/api/v1/risk/var/{bookId}/intraday", {
        summary = "Get intraday VaR timeline with trade annotations for a portfolio"
        tags = listOf("Intraday VaR")
        request {
            pathParameter<String>("bookId") { description = "Portfolio identifier" }
            queryParameter<String>("from") {
                description = "Range start (ISO-8601 instant, e.g. 2026-03-25T08:00:00Z)"
                required = true
            }
            queryParameter<String>("to") {
                description = "Range end (ISO-8601 instant, e.g. 2026-03-25T16:00:00Z)"
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

        val result = service.getTimeline(BookId(bookId), from, to)

        call.respond(
            IntradayVaRTimelineResponse(
                bookId = bookId,
                varPoints = result.varPoints.map { it.toDto() },
                tradeAnnotations = result.tradeAnnotations.map { it.toDto() },
            ),
        )
    }
}

private fun IntradayVaRPoint.toDto(): IntradayVaRPointDto = IntradayVaRPointDto(
    timestamp = timestamp.toString(),
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    delta = delta,
    gamma = gamma,
    vega = vega,
)

private fun TradeAnnotation.toDto(): TradeAnnotationDto = TradeAnnotationDto(
    timestamp = timestamp.toString(),
    instrumentId = instrumentId,
    side = side,
    quantity = quantity,
    tradeId = tradeId,
)
