package com.kinetix.audit.routes

import com.kinetix.audit.dto.toResponse
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.AuditHasher
import com.kinetix.audit.persistence.ChainVerificationResult
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.audit.routes.AuditRoutes")

private const val DEFAULT_PAGE_LIMIT = 1000
private const val MAX_PAGE_LIMIT = 10000
private const val VERIFY_BATCH_SIZE = 10000

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
                queryParameter<Long>("afterId") {
                    description = "Return events with id greater than this value (cursor-based pagination)"
                    required = false
                }
                queryParameter<Int>("limit") {
                    description = "Maximum number of events to return (default $DEFAULT_PAGE_LIMIT, max $MAX_PAGE_LIMIT)"
                    required = false
                }
            }
        }) {
            val portfolioId = call.request.queryParameters["portfolioId"]
            val afterId = call.request.queryParameters["afterId"]?.toLongOrNull() ?: 0L
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT)
                .coerceIn(1, MAX_PAGE_LIMIT)

            val events = if (portfolioId != null) {
                repository.findByPortfolioId(portfolioId)
            } else {
                repository.findPage(afterId, limit)
            }
            call.respond(events.map { it.toResponse() })
        }

        get("/verify", {
            summary = "Verify audit chain integrity"
            tags = listOf("Audit")
        }) {
            logger.info("Starting audit chain verification")

            var lastId = 0L
            var previousHash: String? = null
            var totalVerified = 0L
            var valid = true

            do {
                val batch = repository.findPage(afterId = lastId, limit = VERIFY_BATCH_SIZE)
                if (batch.isEmpty()) break

                val result = AuditHasher.verifyChainIncremental(batch, previousHash)
                if (!result.valid) {
                    valid = false
                    break
                }

                previousHash = result.lastHash
                lastId = batch.last().id
                totalVerified += result.eventsVerified
            } while (batch.size == VERIFY_BATCH_SIZE)

            val result = ChainVerificationResult(valid = valid, eventCount = totalVerified.toInt())
            logger.info("Audit chain verification complete: valid={}, eventCount={}", result.valid, result.eventCount)
            call.respond(result)
        }
    }
}
