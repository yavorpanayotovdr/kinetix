package com.kinetix.gateway.websocket

import com.auth0.jwk.JwkProvider
import com.kinetix.gateway.auth.JwtConfig
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Route.pnlWebSocket(broadcaster: PnlBroadcaster, jwtConfig: JwtConfig? = null, jwkProvider: JwkProvider? = null) {
    webSocket("/ws/pnl") {
        if (jwtConfig != null && jwkProvider != null && call.validateWebSocketToken(jwtConfig, jwkProvider) == null) {
            close(WEBSOCKET_UNAUTHORIZED_CLOSE)
            return@webSocket
        }
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = try {
                        Json.decodeFromString<PnlSubscribeMessage>(text)
                    } catch (_: Exception) {
                        send(Frame.Text("""{"error":"Invalid JSON"}"""))
                        continue
                    }
                    when (message.type) {
                        "subscribe" -> broadcaster.subscribe(this, message.bookId)
                        else -> send(Frame.Text("""{"error":"Unknown message type: ${message.type}"}"""))
                    }
                }
            }
        } finally {
            broadcaster.removeSession(this)
        }
    }
}
