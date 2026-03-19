package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.CrossBookVaRRequestDto
import com.kinetix.gateway.dto.CrossBookVaRResponseDto
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.crossBookVaRRoutes(client: RiskServiceClient) {
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
    }
}
