package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eodTimelineRoutes(client: RiskServiceClient) {

    get("/api/v1/risk/eod-timeline/{bookId}", {
        summary = "Get EOD history timeline for a book"
        tags = listOf("EOD History")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("from") {
                description = "Start date (YYYY-MM-DD)"
                required = true
            }
            queryParameter<String>("to") {
                description = "End date (YYYY-MM-DD)"
                required = true
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")

        val from = call.request.queryParameters["from"]
        if (from.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "from parameter is required"))
            return@get
        }

        val to = call.request.queryParameters["to"]
        if (to.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "to parameter is required"))
            return@get
        }

        val result = client.getEodTimeline(bookId, from, to)
        if (result != null) {
            call.respond(result.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No EOD timeline found for book $bookId"))
        }
    }
}
