package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel

class DeliveryRouter(services: List<DeliveryService>) {
    private val serviceMap: Map<DeliveryChannel, DeliveryService> =
        services.associateBy { it.channel }

    suspend fun route(event: AlertEvent, channels: List<DeliveryChannel>) {
        for (channel in channels) {
            serviceMap[channel]?.deliver(event)
        }
    }
}
