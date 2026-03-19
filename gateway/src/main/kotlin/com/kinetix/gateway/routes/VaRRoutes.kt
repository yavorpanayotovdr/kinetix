package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.VaRCalculationRequest
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.varRoutes(client: RiskServiceClient) {
    route("/api/v1/risk/var/{bookId}") {

        post({
            summary = "Calculate VaR"
            tags = listOf("VaR")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val request = call.receive<VaRCalculationRequest>()
            val params = request.toParams(bookId)
            val result = client.calculateVaR(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get({
            summary = "Get latest VaR result"
            tags = listOf("VaR")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
                queryParameter<String>("valuationDate") {
                    description = "Valuation date (YYYY-MM-DD). When set, returns historical snapshot."
                    required = false
                }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val valuationDate = call.request.queryParameters["valuationDate"]?.takeIf { it.isNotBlank() }
            val result = client.getLatestVaR(bookId, valuationDate)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
