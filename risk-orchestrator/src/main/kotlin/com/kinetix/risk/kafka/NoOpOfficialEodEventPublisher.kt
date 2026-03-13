package com.kinetix.risk.kafka

import com.kinetix.risk.model.OfficialEodPromotedEvent
import com.kinetix.risk.service.OfficialEodEventPublisher

class NoOpOfficialEodEventPublisher : OfficialEodEventPublisher {
    override suspend fun publish(event: OfficialEodPromotedEvent) {
        // No-op for dev: avoids requiring Kafka producer
    }
}
