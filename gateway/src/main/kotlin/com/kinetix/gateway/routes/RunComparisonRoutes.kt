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

    post("/api/v1/risk/compare/{bookId}", {
        summary = "Compare two valuation runs by job ID"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val body = call.receive<RunComparisonRequest>()
        if (body.baseJobId == null || body.targetJobId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Both baseJobId and targetJobId are required"),
            )
            return@post
        }
        val result = client.compareRuns(bookId, body.baseJobId, body.targetJobId)
        call.respond(result)
    }

    get("/api/v1/risk/compare/{bookId}/day-over-day", {
        summary = "Day-over-day VaR comparison"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
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
        val bookId = call.requirePathParam("bookId")
        val targetDate = call.request.queryParameters["targetDate"]?.takeIf { it.isNotBlank() }
        val baseDate = call.request.queryParameters["baseDate"]?.takeIf { it.isNotBlank() }
        val result = client.compareDayOverDay(bookId, targetDate, baseDate)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/api/v1/risk/compare/{bookId}/day-over-day/attribution", {
        summary = "Day-over-day VaR attribution (computationally expensive)"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
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
        val bookId = call.requirePathParam("bookId")
        val targetDate = call.request.queryParameters["targetDate"]?.takeIf { it.isNotBlank() }
        val baseDate = call.request.queryParameters["baseDate"]?.takeIf { it.isNotBlank() }
        val result = client.compareDayOverDayAttribution(bookId, targetDate, baseDate)
        call.respond(result)
    }

    post("/api/v1/risk/compare/{bookId}/model", {
        summary = "Compare VaR across two model configurations"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val body = call.receive<JsonObject>()
        val result = client.compareModel(bookId, body)
        call.respond(result)
    }

    get("/api/v1/risk/compare/{bookId}/market-data-quant", {
        summary = "Quantitative diff for a specific market data item between two manifests"
        tags = listOf("Run Comparison")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("dataType") { description = "Market data type (e.g. SPOT_PRICE)" }
            queryParameter<String>("instrumentId") { description = "Instrument identifier" }
            queryParameter<String>("baseManifestId") { description = "Base manifest UUID" }
            queryParameter<String>("targetManifestId") { description = "Target manifest UUID" }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val dataType = call.request.queryParameters["dataType"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "dataType is required"))
        val instrumentId = call.request.queryParameters["instrumentId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "instrumentId is required"))
        val baseManifestId = call.request.queryParameters["baseManifestId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "baseManifestId is required"))
        val targetManifestId = call.request.queryParameters["targetManifestId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "targetManifestId is required"))

        val result = client.getMarketDataQuantDiff(bookId, dataType, instrumentId, baseManifestId, targetManifestId)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
