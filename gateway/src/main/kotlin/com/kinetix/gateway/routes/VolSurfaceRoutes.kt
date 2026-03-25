package com.kinetix.gateway.routes

import com.kinetix.gateway.client.VolatilityServiceClient
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.volSurfaceRoutes(client: VolatilityServiceClient) {
    route("/api/v1/volatility/{instrumentId}/surface") {

        get({
            summary = "Get latest volatility surface"
            tags = listOf("Volatility")
            request {
                pathParameter<String>("instrumentId") { description = "Instrument identifier" }
            }
        }) {
            val instrumentId = call.requirePathParam("instrumentId")
            val surface = client.getSurface(instrumentId)
            if (surface != null) {
                call.respond(surface)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        route("/diff") {
            get({
                summary = "Get vol surface day-over-day diff"
                tags = listOf("Volatility")
                request {
                    pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                    queryParameter<String>("compareDate") { description = "ISO-8601 instant to compare against" }
                }
            }) {
                val instrumentId = call.requirePathParam("instrumentId")
                val compareDate = call.request.queryParameters["compareDate"]
                    ?: throw IllegalArgumentException("Missing required query parameter: compareDate")
                val diff = client.getSurfaceDiff(instrumentId, compareDate)
                if (diff != null) {
                    call.respond(diff)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
