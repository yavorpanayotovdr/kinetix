package com.kinetix.correlation.kafka

import com.kinetix.common.model.CorrelationMatrix

interface CorrelationPublisher {
    suspend fun publish(matrix: CorrelationMatrix)
}
