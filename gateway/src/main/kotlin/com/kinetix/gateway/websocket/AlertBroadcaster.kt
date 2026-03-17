package com.kinetix.gateway.websocket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AlertWebSocketMessage(
    val type: String = "alert",
    val eventType: String,
    val alert: com.kinetix.gateway.dto.AlertEventDto,
)

class AlertBroadcaster {

    private val json = Json { encodeDefaults = true }
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketServerSession>()

    fun addSession(session: WebSocketServerSession) {
        sessions.add(session)
    }

    fun removeSession(session: WebSocketServerSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(message: AlertWebSocketMessage) {
        val text = json.encodeToString(message)
        val dead = mutableListOf<WebSocketServerSession>()
        for (session in sessions) {
            try {
                session.send(Frame.Text(text))
            } catch (_: Exception) {
                dead.add(session)
            }
        }
        for (session in dead) {
            removeSession(session)
        }
    }
}
