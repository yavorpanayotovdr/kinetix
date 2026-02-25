package com.kinetix.gateway.routes

import com.kinetix.gateway.client.NotificationServiceClient
import com.kinetix.gateway.dto.CreateAlertRuleRequest
import com.kinetix.gateway.dto.toDto
import com.kinetix.gateway.dto.toParams
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(client: NotificationServiceClient) {
    route("/api/v1/notifications") {
        get("/rules", {
            summary = "List alert rules"
            tags = listOf("Notifications")
        }) {
            val rules = client.listRules()
            call.respond(rules.map { it.toDto() })
        }

        post("/rules", {
            summary = "Create alert rule"
            tags = listOf("Notifications")
        }) {
            val request = call.receive<CreateAlertRuleRequest>()
            val result = client.createRule(request.toParams())
            call.respond(HttpStatusCode.Created, result.toDto())
        }

        delete("/rules/{ruleId}", {
            summary = "Delete alert rule"
            tags = listOf("Notifications")
            request {
                pathParameter<String>("ruleId") { description = "Rule identifier" }
            }
        }) {
            val ruleId = call.requirePathParam("ruleId")
            val deleted = client.deleteRule(ruleId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/alerts", {
            summary = "List alerts"
            tags = listOf("Notifications")
            request {
                queryParameter<Int>("limit") {
                    description = "Maximum number of alerts to return"
                    required = false
                }
            }
        }) {
            val limit = call.queryParameters["limit"]?.toIntOrNull() ?: 50
            val alerts = client.listAlerts(limit)
            call.respond(alerts.map { it.toDto() })
        }
    }
}
