package com.kinetix.position.kafka

import com.kinetix.common.model.Trade

interface TradeEventPublisher {
    suspend fun publish(trade: Trade)
}
