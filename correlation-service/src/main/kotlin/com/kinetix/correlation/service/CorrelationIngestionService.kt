package com.kinetix.correlation.service

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.correlation.cache.CorrelationCache
import com.kinetix.correlation.kafka.CorrelationPublisher
import com.kinetix.correlation.persistence.CorrelationMatrixRepository

class CorrelationIngestionService(
    private val repository: CorrelationMatrixRepository,
    private val cache: CorrelationCache,
    private val publisher: CorrelationPublisher,
) {
    suspend fun ingest(matrix: CorrelationMatrix) {
        repository.save(matrix)
        cache.put(matrix.labels, matrix.windowDays, matrix)
        publisher.publish(matrix)
    }
}
