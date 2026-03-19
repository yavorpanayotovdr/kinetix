package com.kinetix.audit.routes

import com.kinetix.audit.persistence.AuditEventRepository
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AuditCountResponse(val count: Long)

fun Route.internalRoutes(repository: AuditEventRepository) {
    route("/api/v1/internal/audit") {
        get("/count") {
            val sinceParam = call.request.queryParameters["since"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "missing required query parameter: since"),
                )
            val since = try {
                Instant.parse(sinceParam)
            } catch (e: Exception) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "invalid timestamp format for parameter: since"),
                )
            }
            val count = repository.countSince(since)
            call.respond(AuditCountResponse(count))
        }
    }
}
