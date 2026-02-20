package com.kinetix.gateway.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Route.marketDataWebSocket(broadcaster: PriceBroadcaster) {
    webSocket("/ws/market-data") {
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = try {
                        Json.decodeFromString<ClientMessage>(text)
                    } catch (_: Exception) {
                        send(Frame.Text("""{"error":"Invalid JSON"}"""))
                        continue
                    }
                    when (message.type) {
                        "subscribe" -> broadcaster.subscribe(this, message.instrumentIds)
                        "unsubscribe" -> broadcaster.unsubscribe(this, message.instrumentIds)
                        else -> send(Frame.Text("""{"error":"Unknown message type: ${message.type}"}"""))
                    }
                }
            }
        } finally {
            broadcaster.removeSession(this)
        }
    }
}
