package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.AlertStatus
import com.kinetix.notification.model.DeliveryChannel
import com.kinetix.notification.persistence.AlertEventRepository
import org.slf4j.LoggerFactory

class InAppDeliveryService(val repository: AlertEventRepository) : DeliveryService {
    private val logger = LoggerFactory.getLogger(InAppDeliveryService::class.java)

    override val channel: DeliveryChannel = DeliveryChannel.IN_APP

    override suspend fun deliver(event: AlertEvent) {
        repository.save(event)
        logger.info("In-app alert delivered: rule={}, severity={}", event.ruleName, event.severity)
    }

    suspend fun getRecentAlerts(limit: Int = 50, status: AlertStatus? = null): List<AlertEvent> {
        return repository.findRecent(limit, status)
    }
}
