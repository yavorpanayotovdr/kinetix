package com.kinetix.gateway.routes

import com.kinetix.gateway.client.NotificationServiceClient
import com.kinetix.gateway.dto.CreateAlertRuleRequest
import com.kinetix.gateway.dto.toDto
import com.kinetix.gateway.dto.toParams
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(client: NotificationServiceClient) {
    route("/api/v1/notifications") {
        get("/rules") {
            val rules = client.listRules()
            call.respond(rules.map { it.toDto() })
        }

        post("/rules") {
            val request = call.receive<CreateAlertRuleRequest>()
            val result = client.createRule(request.toParams())
            call.respond(HttpStatusCode.Created, result.toDto())
        }

        delete("/rules/{ruleId}") {
            val ruleId = call.parameters["ruleId"]!!
            val deleted = client.deleteRule(ruleId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/alerts") {
            val limit = call.queryParameters["limit"]?.toIntOrNull() ?: 50
            val alerts = client.listAlerts(limit)
            call.respond(alerts.map { it.toDto() })
        }
    }
}
