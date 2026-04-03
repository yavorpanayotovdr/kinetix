package com.kinetix.audit.routes

import com.kinetix.audit.dlq.DlqReplayService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.audit.routes.DlqReplayRoutes")

fun Route.dlqReplayRoutes(replayService: DlqReplayService) {
    route("/api/v1/audit") {
        post("/dlq/replay") {
            logger.info("DLQ replay requested")
            val result = replayService.replay()
            call.respond(result)
        }
    }
}
