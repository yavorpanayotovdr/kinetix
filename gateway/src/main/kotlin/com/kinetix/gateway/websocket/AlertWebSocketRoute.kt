package com.kinetix.gateway.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Route.alertWebSocket(broadcaster: AlertBroadcaster) {
    webSocket("/ws/alerts") {
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
