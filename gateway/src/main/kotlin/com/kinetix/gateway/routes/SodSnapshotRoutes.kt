package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.ErrorResponse
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sodSnapshotRoutes(client: RiskServiceClient) {
    get("/api/v1/risk/sod-snapshot/{bookId}/status", {
        summary = "Get SOD baseline status"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val status = client.getSodBaselineStatus(bookId)
        if (status != null) {
            call.respond(status.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/sod-snapshot/{bookId}", {
        summary = "Create manual SOD snapshot"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("jobId") {
                description = "Optional VaR job ID to use as baseline source"
                required = false
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val jobId = call.request.queryParameters["jobId"]
        try {
            val status = client.createSodSnapshot(bookId, jobId)
            call.response.status(HttpStatusCode.Created)
            call.respond(status.toResponse())
        } catch (e: IllegalStateException) {
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("no_valuation_data", e.message ?: "Cannot create snapshot"),
            )
        }
    }

    delete("/api/v1/risk/sod-snapshot/{bookId}", {
        summary = "Reset SOD baseline"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        client.resetSodBaseline(bookId)
        call.respond(HttpStatusCode.NoContent, "")
    }

    get("/api/v1/risk/pnl-attribution/{bookId}", {
        summary = "Get P&L attribution"
        tags = listOf("P&L Attribution")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("date") {
                description = "Attribution date (ISO-8601). Defaults to latest."
                required = false
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val date = call.request.queryParameters["date"]
        val result = client.getPnlAttribution(bookId, date)
        if (result != null) {
            call.respond(result.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/pnl-attribution/{bookId}/compute", {
        summary = "Compute P&L attribution"
        tags = listOf("P&L Attribution")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        try {
            val result = client.computePnlAttribution(bookId)
            call.respond(result.toResponse())
        } catch (e: IllegalStateException) {
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("computation_failed", e.message ?: "Cannot compute P&L attribution"),
            )
        }
    }
}
