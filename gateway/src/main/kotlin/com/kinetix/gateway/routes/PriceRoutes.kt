package com.kinetix.gateway.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.gateway.client.PriceServiceClient
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.priceRoutes(client: PriceServiceClient) {
    route("/api/v1/prices/{instrumentId}") {

        get("/latest", {
            summary = "Get latest price"
            tags = listOf("Prices")
            request {
                pathParameter<String>("instrumentId") { description = "Instrument identifier" }
            }
        }) {
            val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
            val point = client.getLatestPrice(instrumentId)
            if (point != null) {
                call.respond(point.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/history", {
            summary = "Get price history"
            tags = listOf("Prices")
            request {
                pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                queryParameter<String>("from") { description = "Start timestamp (ISO-8601)" }
                queryParameter<String>("to") { description = "End timestamp (ISO-8601)" }
            }
        }) {
            val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
            val from = call.queryParameters["from"]
                ?: throw IllegalArgumentException("Missing required query parameter: from")
            val to = call.queryParameters["to"]
                ?: throw IllegalArgumentException("Missing required query parameter: to")
            val points = client.getPriceHistory(instrumentId, Instant.parse(from), Instant.parse(to))
            call.respond(points.map { it.toResponse() })
        }
    }
}
