package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toPositionRiskResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.positionRiskRoutes(client: RiskServiceClient) {
    get("/api/v1/risk/positions/{bookId}", {
        summary = "Get position-level risk breakdown"
        tags = listOf("Position Risk")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("valuationDate") {
                description = "Valuation date (YYYY-MM-DD). When set, returns historical snapshot."
                required = false
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val valuationDate = call.request.queryParameters["valuationDate"]?.takeIf { it.isNotBlank() }
        val result = client.getPositionRisk(bookId, valuationDate)
        if (result != null) {
            call.respond(result.map { it.toPositionRiskResponse() })
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
