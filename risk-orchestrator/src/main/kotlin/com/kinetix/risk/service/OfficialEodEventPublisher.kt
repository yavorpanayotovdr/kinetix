package com.kinetix.risk.service

import com.kinetix.risk.model.OfficialEodPromotedEvent

interface OfficialEodEventPublisher {
    suspend fun publish(event: OfficialEodPromotedEvent)
}
