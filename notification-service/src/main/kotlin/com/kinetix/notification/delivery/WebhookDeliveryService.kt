package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel
import org.slf4j.LoggerFactory

class WebhookDeliveryService : DeliveryService {
    private val logger = LoggerFactory.getLogger(WebhookDeliveryService::class.java)
    private val _sentWebhooks = mutableListOf<AlertEvent>()
    val sentWebhooks: List<AlertEvent> get() = _sentWebhooks.toList()

    override val channel: DeliveryChannel = DeliveryChannel.WEBHOOK

    override suspend fun deliver(event: AlertEvent) {
        _sentWebhooks.add(event)
        logger.info("Webhook alert sent: rule={}, severity={}, portfolio={}", event.ruleName, event.severity, event.portfolioId)
    }
}
