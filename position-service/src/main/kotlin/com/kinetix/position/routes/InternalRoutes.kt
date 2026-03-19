package com.kinetix.position.routes

import com.kinetix.position.persistence.TradeEventRepository
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TradeCountResponse(val count: Long)

fun Route.internalRoutes(tradeEventRepository: TradeEventRepository) {
    route("/api/v1/internal/trades") {
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
            val count = tradeEventRepository.countSince(since)
            call.respond(TradeCountResponse(count))
        }
    }
}
