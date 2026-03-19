package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.GenerateReportRequest
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.regulatoryRoutes(client: RiskServiceClient) {
    route("/api/v1/regulatory/frtb/{bookId}") {
        post({
            summary = "Calculate FRTB"
            tags = listOf("Regulatory")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val result = client.calculateFrtb(bookId)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("/api/v1/regulatory/report/{bookId}") {
        post({
            summary = "Generate regulatory report"
            tags = listOf("Regulatory")
            request {
                pathParameter<String>("bookId") { description = "Book identifier" }
            }
        }) {
            val bookId = call.requirePathParam("bookId")
            val request = call.receive<GenerateReportRequest>()
            val format = request.format ?: "CSV"
            val result = client.generateReport(bookId, format)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
