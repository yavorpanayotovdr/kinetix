package com.kinetix.audit.routes

import com.kinetix.audit.dto.toResponse
import com.kinetix.audit.persistence.AuditEventRepository
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.auditRoutes(repository: AuditEventRepository) {
    route("/api/v1/audit") {
        get("/events") {
            val portfolioId = call.request.queryParameters["portfolioId"]
            val events = if (portfolioId != null) {
                repository.findByPortfolioId(portfolioId)
            } else {
                repository.findAll()
            }
            call.respond(events.map { it.toResponse() })
        }
    }
}
