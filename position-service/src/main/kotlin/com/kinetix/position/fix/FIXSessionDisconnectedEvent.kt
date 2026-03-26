package com.kinetix.position.fix

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FIXSessionDisconnectedEvent(
    val sessionId: String,
    val counterparty: String,
    val occurredAt: String,
)
