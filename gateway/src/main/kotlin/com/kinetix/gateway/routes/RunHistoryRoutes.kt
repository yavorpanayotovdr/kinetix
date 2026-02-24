package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.runHistoryRoutes(client: RiskServiceClient) {

    get("/api/v1/risk/runs/{portfolioId}") {
        val portfolioId = call.requirePathParam("portfolioId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val runs = client.listCalculationRuns(portfolioId, limit, offset)
        call.respond(runs.map { it.toResponse() })
    }

    get("/api/v1/risk/runs/detail/{runId}") {
        val runId = call.requirePathParam("runId")
        val run = client.getCalculationRunDetail(runId)
        if (run != null) {
            call.respond(run.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
