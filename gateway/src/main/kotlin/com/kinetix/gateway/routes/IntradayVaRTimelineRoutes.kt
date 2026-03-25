package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.intradayVaRTimelineProxyRoutes(riskClient: RiskServiceClient) {
    get("/api/v1/risk/var/{bookId}/intraday", {
        summary = "Get intraday VaR timeline for a portfolio"
        tags = listOf("Intraday VaR")
        request {
            pathParameter<String>("bookId") { description = "Portfolio identifier" }
            queryParameter<String>("from") {
                description = "Series start (ISO-8601 instant)"
                required = true
            }
            queryParameter<String>("to") {
                description = "Series end (ISO-8601 instant)"
                required = true
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val from = call.request.queryParameters["from"]
        val to = call.request.queryParameters["to"]

        if (from == null || to == null) {
            call.respond(HttpStatusCode.BadRequest, "Both 'from' and 'to' query parameters are required")
            return@get
        }

        val result = riskClient.getIntradayVaRTimeline(bookId, from, to)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
