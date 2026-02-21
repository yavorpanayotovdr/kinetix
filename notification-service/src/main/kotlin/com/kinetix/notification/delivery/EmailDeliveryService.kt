package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel
import org.slf4j.LoggerFactory

class EmailDeliveryService : DeliveryService {
    private val logger = LoggerFactory.getLogger(EmailDeliveryService::class.java)
    private val _sentEmails = mutableListOf<AlertEvent>()
    val sentEmails: List<AlertEvent> get() = _sentEmails.toList()

    override val channel: DeliveryChannel = DeliveryChannel.EMAIL

    override suspend fun deliver(event: AlertEvent) {
        _sentEmails.add(event)
        logger.info("Email alert sent: rule={}, severity={}, portfolio={}", event.ruleName, event.severity, event.portfolioId)
    }
}
