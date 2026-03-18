package com.kinetix.gateway.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

fun Route.auditProxyRoutes(httpClient: HttpClient, auditBaseUrl: String) {
    route("/api/v1/audit") {
        get("/events", {
            summary = "List audit events"
            tags = listOf("Audit")
            request {
                queryParameter<String>("portfolioId") { description = "Filter by portfolio ID"; required = false }
                queryParameter<Long>("afterId") { description = "Cursor for pagination"; required = false }
                queryParameter<Int>("limit") { description = "Max events to return"; required = false }
            }
        }) {
            val queryString = call.request.queryParameters.let { params ->
                val parts = mutableListOf<String>()
                params["portfolioId"]?.let { parts.add("portfolioId=$it") }
                params["afterId"]?.let { parts.add("afterId=$it") }
                params["limit"]?.let { parts.add("limit=$it") }
                if (parts.isNotEmpty()) "?${parts.joinToString("&")}" else ""
            }
            val response = httpClient.get("$auditBaseUrl/api/v1/audit/events$queryString")
            val events: JsonArray = response.body()
            call.respond(events)
        }

        get("/verify", {
            summary = "Verify audit chain integrity"
            tags = listOf("Audit")
        }) {
            val response = httpClient.get("$auditBaseUrl/api/v1/audit/verify")
            val result: JsonObject = response.body()
            call.respond(result)
        }
    }
}
