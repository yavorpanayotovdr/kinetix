package com.kinetix.regulatory.historical

import com.kinetix.regulatory.historical.dto.CustomReplayRequest
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.historicalScenarioRoutes(replayService: HistoricalReplayService) {
    route("/api/v1/historical-scenarios") {
        post("/custom-replay", {
            summary = "Replay arbitrary historical date range using live price-service data"
            tags = listOf("Historical Scenarios")
            request {
                body<CustomReplayRequest>()
            }
        }) {
            val request = call.receive<CustomReplayRequest>()
            val result = replayService.replayCustomDateRange(
                bookId = request.bookId,
                instrumentIds = request.instrumentIds,
                startDate = request.startDate,
                endDate = request.endDate,
            )
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
