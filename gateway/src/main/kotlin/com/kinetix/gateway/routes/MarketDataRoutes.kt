package com.kinetix.gateway.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.gateway.client.MarketDataServiceClient
import com.kinetix.gateway.dto.toResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.marketDataRoutes(client: MarketDataServiceClient) {
    route("/api/v1/market-data/{instrumentId}") {

        get("/latest") {
            val instrumentId = InstrumentId(call.parameters["instrumentId"]!!)
            val point = client.getLatestPrice(instrumentId)
            if (point != null) {
                call.respond(point.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/history") {
            val instrumentId = InstrumentId(call.parameters["instrumentId"]!!)
            val from = call.queryParameters["from"]
                ?: throw IllegalArgumentException("Missing required query parameter: from")
            val to = call.queryParameters["to"]
                ?: throw IllegalArgumentException("Missing required query parameter: to")
            val points = client.getPriceHistory(instrumentId, Instant.parse(from), Instant.parse(to))
            call.respond(points.map { it.toResponse() })
        }
    }
}
