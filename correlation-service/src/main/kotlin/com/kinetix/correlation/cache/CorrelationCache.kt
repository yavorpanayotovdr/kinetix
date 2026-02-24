package com.kinetix.correlation.cache

import com.kinetix.common.model.CorrelationMatrix

interface CorrelationCache {
    suspend fun put(labels: List<String>, windowDays: Int, matrix: CorrelationMatrix)
    suspend fun get(labels: List<String>, windowDays: Int): CorrelationMatrix?
}
