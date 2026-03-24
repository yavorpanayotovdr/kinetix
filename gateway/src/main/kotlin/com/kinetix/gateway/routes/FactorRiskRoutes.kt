package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.factorRiskRoutes(riskClient: RiskServiceClient) {

    get("/api/v1/books/{bookId}/factor-risk/latest") {
        val bookId = call.requirePathParam("bookId")
        val result = riskClient.getLatestFactorRisk(bookId)

        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    get("/api/v1/books/{bookId}/factor-risk") {
        val bookId = call.requirePathParam("bookId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val results = riskClient.getFactorRiskHistory(bookId, limit)
        call.respond(results)
    }
}
