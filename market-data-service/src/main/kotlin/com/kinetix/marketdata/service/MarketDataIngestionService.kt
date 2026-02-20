package com.kinetix.marketdata.service

import com.kinetix.common.model.MarketDataPoint
import com.kinetix.marketdata.cache.MarketDataCache
import com.kinetix.marketdata.kafka.MarketDataPublisher
import com.kinetix.marketdata.persistence.MarketDataRepository

class MarketDataIngestionService(
    private val repository: MarketDataRepository,
    private val cache: MarketDataCache,
    private val publisher: MarketDataPublisher,
) {
    suspend fun ingest(point: MarketDataPoint) {
        repository.save(point)
        cache.put(point)
        publisher.publish(point)
    }
}
