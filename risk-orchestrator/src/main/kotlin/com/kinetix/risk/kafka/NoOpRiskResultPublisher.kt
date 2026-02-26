package com.kinetix.risk.kafka

import com.kinetix.risk.model.ValuationResult

class NoOpRiskResultPublisher : RiskResultPublisher {
    override suspend fun publish(result: ValuationResult) {
        // No-op for dev: avoids requiring Kafka producer
    }
}
