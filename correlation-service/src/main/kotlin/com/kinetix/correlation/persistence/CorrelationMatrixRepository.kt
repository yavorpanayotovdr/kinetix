package com.kinetix.correlation.persistence

import com.kinetix.common.model.CorrelationMatrix
import java.time.Instant

interface CorrelationMatrixRepository {
    suspend fun save(matrix: CorrelationMatrix)
    suspend fun findLatest(labels: List<String>, windowDays: Int): CorrelationMatrix?
    suspend fun findByTimeRange(labels: List<String>, windowDays: Int, from: Instant, to: Instant): List<CorrelationMatrix>
}
