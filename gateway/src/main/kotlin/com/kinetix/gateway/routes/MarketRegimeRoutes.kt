package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.marketRegimeRoutes(riskClient: RiskServiceClient) {
    get("/api/v1/risk/regime/current", {
        summary = "Get the current market regime and adaptive VaR parameters"
        tags = listOf("Market Regime")
    }) {
        val result = riskClient.getCurrentRegime()
        call.respond(result)
    }

    get("/api/v1/risk/regime/history", {
        summary = "Get market regime history in descending order"
        tags = listOf("Market Regime")
        request {
            queryParameter<Int>("limit") {
                description = "Maximum number of records to return (default 50)"
                required = false
            }
        }
    }) {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val result = riskClient.getRegimeHistory(limit)
        call.respond(result)
    }
}
