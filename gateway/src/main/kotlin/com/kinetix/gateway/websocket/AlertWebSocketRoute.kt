package com.kinetix.gateway.websocket

import com.auth0.jwk.JwkProvider
import com.kinetix.gateway.auth.JwtConfig
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Route.alertWebSocket(broadcaster: AlertBroadcaster, jwtConfig: JwtConfig? = null, jwkProvider: JwkProvider? = null) {
    webSocket("/ws/alerts") {
        if (jwtConfig != null && jwkProvider != null && call.validateWebSocketToken(jwtConfig, jwkProvider) == null) {
            close(WEBSOCKET_UNAUTHORIZED_CLOSE)
            return@webSocket
        }
        broadcaster.addSession(this)
        try {
            for (frame in incoming) {
                // Alerts WebSocket is push-only; ignore client messages.
                if (frame is Frame.Text) {
                    send(Frame.Text("""{"info":"This is a push-only channel"}"""))
                }
            }
        } finally {
            broadcaster.removeSession(this)
        }
    }
}
