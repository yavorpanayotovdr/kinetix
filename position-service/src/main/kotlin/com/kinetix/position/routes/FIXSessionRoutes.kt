package com.kinetix.position.routes

import com.kinetix.position.fix.FIXSessionDisconnectedEvent
import com.kinetix.position.fix.FIXSessionEventPublisher
import com.kinetix.position.fix.FIXSessionRepository
import com.kinetix.position.fix.FIXSessionStatus
import com.kinetix.position.routes.dtos.FIXSessionResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
private data class UpdateSessionStatusRequest(val status: String)

fun Route.fixSessionRoutes(
    fixSessionRepository: FIXSessionRepository,
    sessionEventPublisher: FIXSessionEventPublisher? = null,
) {
    route("/api/v1/fix") {
        get("/sessions", {
            summary = "List all FIX sessions and their connectivity status"
            tags = listOf("Execution")
            response {
                code(HttpStatusCode.OK) { body<List<FIXSessionResponse>>() }
            }
        }) {
            val sessions = fixSessionRepository.findAll()
            call.respond(sessions.map { session ->
                FIXSessionResponse(
                    sessionId = session.sessionId,
                    counterparty = session.counterparty,
                    status = session.status.name,
                    lastMessageAt = session.lastMessageAt?.toString(),
                    inboundSeqNum = session.inboundSeqNum,
                    outboundSeqNum = session.outboundSeqNum,
                )
            })
        }

        patch("/sessions/{sessionId}/status", {
            summary = "Update FIX session connectivity status"
            tags = listOf("Execution")
            request {
                pathParameter<String>("sessionId") { description = "FIX session identifier" }
                body<UpdateSessionStatusRequest>()
            }
            response {
                code(HttpStatusCode.NoContent) { }
                code(HttpStatusCode.BadRequest) { }
                code(HttpStatusCode.NotFound) { }
            }
        }) {
            val sessionId = call.requirePathParam("sessionId")
            val request = call.receive<UpdateSessionStatusRequest>()
            val status = runCatching { FIXSessionStatus.valueOf(request.status) }.getOrElse {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Invalid status: ${request.status}. Must be one of ${FIXSessionStatus.entries.map { it.name }}",
                )
                return@patch
            }

            val session = fixSessionRepository.findById(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, "FIX session not found: $sessionId")
                return@patch
            }

            fixSessionRepository.updateStatus(sessionId, status)

            if (status == FIXSessionStatus.DISCONNECTED) {
                sessionEventPublisher?.publishDisconnected(
                    FIXSessionDisconnectedEvent(
                        sessionId = sessionId,
                        counterparty = session.counterparty,
                        occurredAt = Instant.now().toString(),
                    )
                )
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
