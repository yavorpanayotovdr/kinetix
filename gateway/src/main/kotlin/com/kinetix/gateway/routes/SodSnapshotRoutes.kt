package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sodSnapshotRoutes(client: RiskServiceClient) {
    get("/api/v1/risk/sod-snapshot/{portfolioId}/status", {
        summary = "Get SOD baseline status"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val status = client.getSodBaselineStatus(portfolioId)
        if (status != null) {
            call.respond(status.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/sod-snapshot/{portfolioId}", {
        summary = "Create manual SOD snapshot"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val status = client.createSodSnapshot(portfolioId)
        call.response.status(HttpStatusCode.Created)
        call.respond(status.toResponse())
    }

    delete("/api/v1/risk/sod-snapshot/{portfolioId}", {
        summary = "Reset SOD baseline"
        tags = listOf("SOD Snapshot")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        client.resetSodBaseline(portfolioId)
        call.respond(HttpStatusCode.NoContent, "")
    }

    get("/api/v1/risk/pnl-attribution/{portfolioId}", {
        summary = "Get P&L attribution"
        tags = listOf("P&L Attribution")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("date") {
                description = "Attribution date (ISO-8601). Defaults to latest."
                required = false
            }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val date = call.request.queryParameters["date"]
        val result = client.getPnlAttribution(portfolioId, date)
        if (result != null) {
            call.respond(result.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/pnl-attribution/{portfolioId}/compute", {
        summary = "Compute P&L attribution"
        tags = listOf("P&L Attribution")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val result = client.computePnlAttribution(portfolioId)
        call.respond(result.toResponse())
    }
}
