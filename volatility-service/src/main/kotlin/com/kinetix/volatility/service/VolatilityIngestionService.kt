package com.kinetix.volatility.service

import com.kinetix.common.model.VolSurface
import com.kinetix.volatility.cache.VolatilityCache
import com.kinetix.volatility.kafka.VolatilityPublisher
import com.kinetix.volatility.persistence.VolSurfaceRepository

class VolatilityIngestionService(
    private val repository: VolSurfaceRepository,
    private val cache: VolatilityCache,
    private val publisher: VolatilityPublisher,
) {
    suspend fun ingest(surface: VolSurface) {
        repository.save(surface)
        cache.putSurface(surface)
        publisher.publishSurface(surface)
    }
}
