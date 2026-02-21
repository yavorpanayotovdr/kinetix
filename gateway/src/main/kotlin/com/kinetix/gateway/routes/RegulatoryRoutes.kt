package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.GenerateReportRequest
import com.kinetix.gateway.dto.toResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.regulatoryRoutes(client: RiskServiceClient) {
    route("/api/v1/regulatory/frtb/{portfolioId}") {
        post {
            val portfolioId = call.parameters["portfolioId"]!!
            val result = client.calculateFrtb(portfolioId)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("/api/v1/regulatory/report/{portfolioId}") {
        post {
            val portfolioId = call.parameters["portfolioId"]!!
            val request = call.receive<GenerateReportRequest>()
            val format = request.format ?: "CSV"
            val result = client.generateReport(portfolioId, format)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
