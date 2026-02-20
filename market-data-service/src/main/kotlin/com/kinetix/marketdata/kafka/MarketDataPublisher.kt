package com.kinetix.marketdata.kafka

import com.kinetix.common.model.MarketDataPoint

interface MarketDataPublisher {
    suspend fun publish(point: MarketDataPoint)
}
