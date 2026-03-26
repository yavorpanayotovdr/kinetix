package com.kinetix.gateway.routes

import com.kinetix.gateway.auth.BookAccessService
import com.kinetix.gateway.auth.checkMultiBookAccess
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.CrossBookVaRRequestDto
import com.kinetix.gateway.dto.CrossBookVaRResponseDto
import com.kinetix.gateway.dto.StressedCrossBookVaRRequestDto
import com.kinetix.gateway.dto.StressedCrossBookVaRResponseDto
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.crossBookVaRRoutes(client: RiskServiceClient, bookAccessService: BookAccessService? = null) {
    route("/api/v1/risk/var/cross-book") {

        post({
            summary = "Calculate cross-book aggregated VaR"
            tags = listOf("VaR")
            request {
                body<CrossBookVaRRequestDto>()
            }
            response {
                HttpStatusCode.OK to { body<CrossBookVaRResponseDto>() }
                HttpStatusCode.BadRequest to { description = "Invalid request (e.g. empty bookIds)" }
                HttpStatusCode.NotFound to { description = "No positions found for any of the specified books" }
            }
        }) {
            val request = call.receive<CrossBookVaRRequestDto>()
            if (bookAccessService != null && !call.checkMultiBookAccess(request.bookIds, bookAccessService)) return@post
            val params = request.toParams()
            val result = client.calculateCrossBookVaR(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/{groupId}", {
            summary = "Get cached cross-book VaR result"
            tags = listOf("VaR")
            request {
                pathParameter<String>("groupId") { description = "Portfolio group identifier" }
            }
            response {
                HttpStatusCode.OK to { body<CrossBookVaRResponseDto>() }
                HttpStatusCode.NotFound to { description = "No cached result for this group" }
            }
        }) {
            val groupId = call.requirePathParam("groupId")
            val result = client.getCrossBookVaR(groupId)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/stressed", {
            summary = "Calculate stressed cross-book VaR (correlation spike scenario)"
            description = "Computes cross-book VaR under both normal and stressed correlations, " +
                "showing how diversification benefit erodes when correlations spike to crisis levels."
            tags = listOf("VaR")
            request {
                body<StressedCrossBookVaRRequestDto>()
            }
            response {
                HttpStatusCode.OK to { body<StressedCrossBookVaRResponseDto>() }
                HttpStatusCode.BadRequest to { description = "Invalid request (e.g. empty bookIds)" }
                HttpStatusCode.NotFound to { description = "No positions found for any of the specified books" }
            }
        }) {
            val request = call.receive<StressedCrossBookVaRRequestDto>()
            if (bookAccessService != null && !call.checkMultiBookAccess(request.bookIds, bookAccessService)) return@post
            val params = request.toParams()
            val result = client.calculateStressedCrossBookVaR(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
