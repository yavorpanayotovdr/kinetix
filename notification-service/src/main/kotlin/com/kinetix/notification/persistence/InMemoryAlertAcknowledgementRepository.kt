package com.kinetix.notification.persistence

import java.util.concurrent.ConcurrentHashMap

class InMemoryAlertAcknowledgementRepository : AlertAcknowledgementRepository {
    private val acks = ConcurrentHashMap<String, AlertAcknowledgement>()

    override suspend fun save(ack: AlertAcknowledgement) {
        acks[ack.id] = ack
    }

    override suspend fun findByAlertId(alertEventId: String): AlertAcknowledgement? =
        acks.values.find { it.alertEventId == alertEventId }
}
