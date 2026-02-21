package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

class InAppDeliveryService(private val maxSize: Int = 100) : DeliveryService {
    private val logger = LoggerFactory.getLogger(InAppDeliveryService::class.java)
    private val alerts = ConcurrentLinkedDeque<AlertEvent>()

    override val channel: DeliveryChannel = DeliveryChannel.IN_APP

    override suspend fun deliver(event: AlertEvent) {
        alerts.addFirst(event)
        while (alerts.size > maxSize) {
            alerts.removeLast()
        }
        logger.info("In-app alert delivered: rule={}, severity={}", event.ruleName, event.severity)
    }

    fun getRecentAlerts(limit: Int = 50): List<AlertEvent> {
        return alerts.take(limit)
    }
}
