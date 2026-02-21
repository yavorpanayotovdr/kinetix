package com.kinetix.notification.delivery

import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.DeliveryChannel

interface DeliveryService {
    suspend fun deliver(event: AlertEvent)
    val channel: DeliveryChannel
}
