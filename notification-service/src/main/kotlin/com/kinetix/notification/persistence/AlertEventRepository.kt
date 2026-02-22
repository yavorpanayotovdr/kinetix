package com.kinetix.notification.persistence

import com.kinetix.notification.model.AlertEvent

interface AlertEventRepository {
    suspend fun save(event: AlertEvent)
    suspend fun findRecent(limit: Int = 50): List<AlertEvent>
}
