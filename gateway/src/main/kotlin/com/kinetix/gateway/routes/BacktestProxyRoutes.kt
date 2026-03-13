package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RegulatoryServiceClient
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.backtestProxyRoutes(regulatoryServiceClient: RegulatoryServiceClient) {

    get("/api/v1/regulatory/backtest/compare", {
        summary = "Compare two backtest results"
        tags = listOf("Backtest")
        request {
            queryParameter<String>("baseId") { description = "Base backtest result identifier" }
            queryParameter<String>("targetId") { description = "Target backtest result identifier" }
        }
    }) {
        val baseId = call.request.queryParameters["baseId"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing required query parameter: baseId"),
            )
        val targetId = call.request.queryParameters["targetId"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing required query parameter: targetId"),
            )
        val result = regulatoryServiceClient.compareBacktests(baseId, targetId)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
