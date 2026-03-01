package com.kinetix.audit.routes

import com.kinetix.audit.dto.toResponse
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.AuditHasher
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.audit.routes.AuditRoutes")

fun Route.auditRoutes(repository: AuditEventRepository) {
    route("/api/v1/audit") {
        get("/events", {
            summary = "List audit events"
            tags = listOf("Audit")
            request {
                queryParameter<String>("portfolioId") {
                    description = "Filter by portfolio ID"
                    required = false
                }
            }
        }) {
            val portfolioId = call.request.queryParameters["portfolioId"]
            val events = if (portfolioId != null) {
                repository.findByPortfolioId(portfolioId)
            } else {
                repository.findAll()
            }
            call.respond(events.map { it.toResponse() })
        }

        get("/verify", {
            summary = "Verify audit chain integrity"
            tags = listOf("Audit")
        }) {
            val events = repository.findAll()
            logger.info("Verifying audit chain integrity, eventCount={}", events.size)
            val result = AuditHasher.verifyChain(events)
            logger.info("Audit chain verification complete: valid={}, eventCount={}", result.valid, result.eventCount)
            call.respond(result)
        }
    }
}
