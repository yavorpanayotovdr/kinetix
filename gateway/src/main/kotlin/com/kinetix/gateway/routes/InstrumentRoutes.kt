package com.kinetix.gateway.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InstrumentResponse(
    val instrumentId: String,
    val instrumentType: String,
    val displayName: String,
    val assetClass: String,
    val currency: String,
    val attributes: JsonObject,
    val createdAt: String,
    val updatedAt: String,
)

fun Route.instrumentRoutes(httpClient: HttpClient, referenceDataBaseUrl: String) {
    route("/api/v1/instruments") {
        get({
            summary = "List instruments"
            tags = listOf("Instruments")
            request {
                queryParameter<String>("type") { description = "Filter by instrument type"; required = false }
                queryParameter<String>("assetClass") { description = "Filter by asset class"; required = false }
            }
        }) {
            val queryString = call.request.queryParameters.let { params ->
                val parts = mutableListOf<String>()
                params["type"]?.let { parts.add("type=$it") }
                params["assetClass"]?.let { parts.add("assetClass=$it") }
                if (parts.isNotEmpty()) "?${parts.joinToString("&")}" else ""
            }
            val response = httpClient.get("$referenceDataBaseUrl/api/v1/instruments$queryString")
            val instruments: List<InstrumentResponse> = response.body()
            call.respond(instruments)
        }

        route("/{id}") {
            get({
                summary = "Get instrument by ID"
                tags = listOf("Instruments")
                request {
                    pathParameter<String>("id") { description = "Instrument identifier" }
                }
            }) {
                val id = call.requirePathParam("id")
                val response = httpClient.get("$referenceDataBaseUrl/api/v1/instruments/$id")
                if (response.status == HttpStatusCode.NotFound) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val instrument: InstrumentResponse = response.body()
                    call.respond(instrument)
                }
            }
        }
    }
}
