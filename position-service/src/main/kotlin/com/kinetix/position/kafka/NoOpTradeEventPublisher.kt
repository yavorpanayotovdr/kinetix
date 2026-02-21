package com.kinetix.position.kafka

import com.kinetix.common.model.Trade

class NoOpTradeEventPublisher : TradeEventPublisher {
    override suspend fun publish(trade: Trade) {
        // No-op for dev: avoids requiring Kafka producer
    }
}
