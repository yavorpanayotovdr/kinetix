package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel
import com.kinetix.notification.persistence.AlertEventRepository
import org.slf4j.LoggerFactory

class InAppDeliveryService(private val repository: AlertEventRepository) : DeliveryService {
    private val logger = LoggerFactory.getLogger(InAppDeliveryService::class.java)

    override val channel: DeliveryChannel = DeliveryChannel.IN_APP

    override suspend fun deliver(event: AlertEvent) {
        repository.save(event)
        logger.info("In-app alert delivered: rule={}, severity={}", event.ruleName, event.severity)
    }

    suspend fun getRecentAlerts(limit: Int = 50): List<AlertEvent> {
        return repository.findRecent(limit)
    }
}
