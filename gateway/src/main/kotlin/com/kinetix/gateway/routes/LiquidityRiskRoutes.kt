package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class LiquidityRiskCalculationRequest(val baseVar: Double)

fun Route.liquidityRiskRoutes(riskClient: RiskServiceClient) {
    post("/api/v1/books/{bookId}/liquidity-risk") {
        val bookId = call.requirePathParam("bookId")
        val request = call.receive<LiquidityRiskCalculationRequest>()

        val result = riskClient.calculateLiquidityRisk(bookId, request.baseVar)

        if (result == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(result)
        }
    }

    get("/api/v1/books/{bookId}/liquidity-risk/latest") {
        val bookId = call.requirePathParam("bookId")
        val result = riskClient.getLatestLiquidityRisk(bookId)

        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    get("/api/v1/books/{bookId}/liquidity-risk") {
        val bookId = call.requirePathParam("bookId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val results = riskClient.getLiquidityRiskHistory(bookId, limit)
        call.respond(results)
    }
}
