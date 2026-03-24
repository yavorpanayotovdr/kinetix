package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject

fun Route.counterpartyRiskRoutes(riskClient: RiskServiceClient) {

    get("/api/v1/counterparty-risk") {
        val exposures = riskClient.getAllCounterpartyExposures()
        call.respond(exposures)
    }

    get("/api/v1/counterparty-risk/{counterpartyId}") {
        val counterpartyId = call.requirePathParam("counterpartyId")
        val exposure = riskClient.getCounterpartyExposure(counterpartyId)
        if (exposure == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(exposure)
        }
    }

    get("/api/v1/counterparty-risk/{counterpartyId}/history") {
        val counterpartyId = call.requirePathParam("counterpartyId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 90
        val history = riskClient.getCounterpartyExposureHistory(counterpartyId, limit)
        call.respond(history)
    }

    post("/api/v1/counterparty-risk/{counterpartyId}/pfe") {
        val counterpartyId = call.requirePathParam("counterpartyId")
        val body = call.receive<JsonObject>()
        val result = riskClient.computeCounterpartyPFE(counterpartyId, body)
        if (result == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(result)
        }
    }

    post("/api/v1/counterparty-risk/{counterpartyId}/cva") {
        val counterpartyId = call.requirePathParam("counterpartyId")
        val result = riskClient.computeCounterpartyCVA(counterpartyId)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }
}
