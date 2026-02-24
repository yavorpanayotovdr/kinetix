package com.kinetix.risk.routes

import com.kinetix.risk.mapper.toDetailResponse
import com.kinetix.risk.mapper.toSummaryResponse
import com.kinetix.risk.service.CalculationRunRecorder
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.runHistoryRoutes(runRecorder: CalculationRunRecorder) {

    get("/api/v1/risk/runs/{portfolioId}") {
        val portfolioId = call.requirePathParam("portfolioId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val runs = runRecorder.findByPortfolioId(portfolioId, limit, offset)
        call.respond(runs.map { it.toSummaryResponse() })
    }

    get("/api/v1/risk/runs/detail/{runId}") {
        val runIdStr = call.requirePathParam("runId")
        val runId = try {
            UUID.fromString(runIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val run = runRecorder.findByRunId(runId)
        if (run != null) {
            call.respond(run.toDetailResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
