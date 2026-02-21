package com.kinetix.risk.kafka

import com.kinetix.risk.model.VaRResult

class NoOpRiskResultPublisher : RiskResultPublisher {
    override suspend fun publish(result: VaRResult) {
        // No-op for dev: avoids requiring Kafka producer
    }
}
