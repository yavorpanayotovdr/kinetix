package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toPositionRiskResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.positionRiskRoutes(client: RiskServiceClient) {
    get("/api/v1/risk/positions/{portfolioId}", {
        summary = "Get position-level risk breakdown"
        tags = listOf("Position Risk")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val result = client.getPositionRisk(portfolioId)
        if (result != null) {
            call.respond(result.map { it.toPositionRiskResponse() })
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
