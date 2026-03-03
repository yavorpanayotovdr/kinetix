package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.WhatIfGatewayRequest
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.whatIfRoutes(client: RiskServiceClient) {
    route("/api/v1/risk/what-if/{portfolioId}") {
        post({
            summary = "Run what-if analysis"
            tags = listOf("What-If")
            request {
                pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            }
        }) {
            val portfolioId = call.requirePathParam("portfolioId")
            val request = call.receive<WhatIfGatewayRequest>()
            val params = request.toParams(portfolioId)
            val result = client.runWhatIf(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
