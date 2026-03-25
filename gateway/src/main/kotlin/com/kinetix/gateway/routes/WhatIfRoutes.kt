package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.RebalancingGatewayRequest
import com.kinetix.gateway.dto.WhatIfGatewayRequest
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toRebalancingParams
import com.kinetix.gateway.dto.toRebalancingResponse
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.whatIfRoutes(client: RiskServiceClient) {
    route("/api/v1/risk/what-if/{bookId}") {
        post({
            summary = "Run what-if analysis"
            tags = listOf("What-If")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val request = call.receive<WhatIfGatewayRequest>()
            val params = request.toParams(bookId)
            val result = client.runWhatIf(params)
            call.respond(result.toResponse())
        }

        post("/rebalance", {
            summary = "Run rebalancing what-if analysis"
            tags = listOf("What-If")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val request = call.receive<RebalancingGatewayRequest>()
            val params = request.toRebalancingParams(bookId)
            val result = client.runRebalancing(params)
            call.respond(result.toRebalancingResponse())
        }
    }
}
