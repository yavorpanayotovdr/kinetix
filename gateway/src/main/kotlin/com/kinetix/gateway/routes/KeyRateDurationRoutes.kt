package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.keyRateDurationRoutes(riskClient: RiskServiceClient) {
    get("/api/v1/risk/krd/{bookId}") {
        val bookId = call.requirePathParam("bookId")
        val result = riskClient.getKeyRateDurations(bookId)

        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }
}
