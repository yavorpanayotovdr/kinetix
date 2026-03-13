package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class RunComparisonRequest(
    val baseJobId: String? = null,
    val targetJobId: String? = null,
)

fun Route.runComparisonRoutes(client: RiskServiceClient) {

    post("/api/v1/risk/compare/{portfolioId}", {
        summary = "Compare two valuation runs by job ID"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val body = call.receive<RunComparisonRequest>()
        if (body.baseJobId == null || body.targetJobId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Both baseJobId and targetJobId are required"),
            )
            return@post
        }
        val result = client.compareRuns(portfolioId, body.baseJobId, body.targetJobId)
        call.respond(result)
    }

    get("/api/v1/risk/compare/{portfolioId}/day-over-day", {
        summary = "Day-over-day VaR comparison"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("targetDate") {
                description = "Target date (YYYY-MM-DD)"
                required = false
            }
            queryParameter<String>("baseDate") {
                description = "Base date (YYYY-MM-DD)"
                required = false
            }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val targetDate = call.request.queryParameters["targetDate"]?.takeIf { it.isNotBlank() }
        val baseDate = call.request.queryParameters["baseDate"]?.takeIf { it.isNotBlank() }
        val result = client.compareDayOverDay(portfolioId, targetDate, baseDate)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/compare/{portfolioId}/day-over-day/attribution", {
        summary = "Day-over-day VaR attribution (computationally expensive)"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("targetDate") {
                description = "Target date (YYYY-MM-DD)"
                required = false
            }
            queryParameter<String>("baseDate") {
                description = "Base date (YYYY-MM-DD)"
                required = false
            }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val targetDate = call.request.queryParameters["targetDate"]?.takeIf { it.isNotBlank() }
        val baseDate = call.request.queryParameters["baseDate"]?.takeIf { it.isNotBlank() }
        val result = client.compareDayOverDayAttribution(portfolioId, targetDate, baseDate)
        call.respond(result)
    }

    post("/api/v1/risk/compare/{portfolioId}/model", {
        summary = "Compare VaR across two model configurations"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val body = call.receive<JsonObject>()
        val result = client.compareModel(portfolioId, body)
        call.respond(result)
    }
}
