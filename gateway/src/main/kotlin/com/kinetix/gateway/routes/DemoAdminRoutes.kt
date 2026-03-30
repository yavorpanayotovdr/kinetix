package com.kinetix.gateway.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class DemoResetStatus(
    val position: String,
    val audit: String,
    val risk: String,
)

fun Route.demoAdminRoutes(
    httpClient: HttpClient,
    positionUrl: String,
    auditUrl: String,
    riskUrl: String,
    adminKey: String,
    resetToken: String,
) {
    post("/api/v1/admin/demo-reset") {
        val key = call.request.headers["X-Demo-Admin-Key"]
        if (key != adminKey) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid admin key"))
            return@post
        }

        val tokenHeader = "X-Demo-Reset-Token" to resetToken

        val positionResult = try {
            val resp = httpClient.post("$positionUrl/api/v1/internal/position/demo-reset") {
                header(tokenHeader.first, tokenHeader.second)
            }
            if (resp.status.isSuccess()) "ok" else "failed: ${resp.status}"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        val auditResult = try {
            val resp = httpClient.post("$auditUrl/api/v1/internal/audit/demo-reset") {
                header(tokenHeader.first, tokenHeader.second)
            }
            if (resp.status.isSuccess()) "ok" else "failed: ${resp.status}"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        val riskResult = try {
            val resp = httpClient.post("$riskUrl/api/v1/internal/risk/demo-reset") {
                header(tokenHeader.first, tokenHeader.second)
            }
            if (resp.status.isSuccess()) "ok" else "failed: ${resp.status}"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        call.respond(DemoResetStatus(position = positionResult, audit = auditResult, risk = riskResult))
    }
}
