package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject

fun Route.riskBudgetRoutes(riskClient: RiskServiceClient) {
    route("/api/v1/risk/budgets") {
        get {
            val level = call.request.queryParameters["level"]
            val entityId = call.request.queryParameters["entityId"]
            val result = riskClient.getRiskBudgets(level, entityId)
            call.respond(result)
        }

        post {
            val body = call.receive<JsonObject>()
            val result = riskClient.createRiskBudget(body)
            call.respond(HttpStatusCode.Created, result)
        }

        route("/{id}") {
            get {
                val id = call.requirePathParam("id")
                val result = riskClient.getRiskBudget(id)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(result)
                }
            }

            delete {
                val id = call.requirePathParam("id")
                val deleted = riskClient.deleteRiskBudget(id)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
