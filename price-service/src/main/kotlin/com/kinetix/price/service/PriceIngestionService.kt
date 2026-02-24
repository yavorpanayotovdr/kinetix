package com.kinetix.price.service

import com.kinetix.common.model.PricePoint
import com.kinetix.price.cache.PriceCache
import com.kinetix.price.kafka.PricePublisher
import com.kinetix.price.persistence.PriceRepository

class PriceIngestionService(
    private val repository: PriceRepository,
    private val cache: PriceCache,
    private val publisher: PricePublisher,
) {
    suspend fun ingest(point: PricePoint) {
        repository.save(point)
        cache.put(point)
        publisher.publish(point)
    }
}
