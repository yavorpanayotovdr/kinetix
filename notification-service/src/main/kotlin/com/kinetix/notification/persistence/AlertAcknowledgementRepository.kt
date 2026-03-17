package com.kinetix.notification.persistence

import java.time.Instant

data class AlertAcknowledgement(
    val id: String,
    val alertEventId: String,
    val alertTriggeredAt: Instant,
    val acknowledgedBy: String,
    val acknowledgedAt: Instant,
    val notes: String? = null,
)

interface AlertAcknowledgementRepository {
    suspend fun save(ack: AlertAcknowledgement)
    suspend fun findByAlertId(alertEventId: String): AlertAcknowledgement?
}
