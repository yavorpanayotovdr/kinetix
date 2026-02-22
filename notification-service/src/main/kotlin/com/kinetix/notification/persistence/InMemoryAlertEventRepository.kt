package com.kinetix.notification.persistence

import com.kinetix.notification.model.AlertEvent
import java.util.concurrent.ConcurrentLinkedDeque

class InMemoryAlertEventRepository : AlertEventRepository {
    private val events = ConcurrentLinkedDeque<AlertEvent>()

    override suspend fun save(event: AlertEvent) {
        events.addFirst(event)
    }

    override suspend fun findRecent(limit: Int): List<AlertEvent> = events.take(limit)
}
