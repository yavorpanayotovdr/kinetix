package com.kinetix.price.service

import com.kinetix.common.model.PricePoint
import com.kinetix.price.cache.PriceCache
import com.kinetix.price.kafka.PricePublisher
import com.kinetix.price.metrics.PriceMetrics
import com.kinetix.price.metrics.PriceStalenessTracker
import com.kinetix.price.persistence.PriceRepository
import java.time.Clock
import java.time.Duration

class PriceIngestionService(
    private val repository: PriceRepository,
    private val cache: PriceCache,
    private val publisher: PricePublisher,
    private val stalenessTracker: PriceStalenessTracker? = null,
    private val priceMetrics: PriceMetrics? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun ingest(point: PricePoint) {
        val start = clock.instant()
        repository.save(point)
        cache.put(point)
        publisher.publish(point)
        stalenessTracker?.recordUpdate(point.instrumentId)
        priceMetrics?.recordUpdate()
        priceMetrics?.recordLatency(Duration.between(start, clock.instant()))
    }
}
