package com.kinetix.risk.kafka

import com.kinetix.risk.model.CrossBookValuationResult

interface CrossBookRiskResultPublisher {
    suspend fun publish(result: CrossBookValuationResult, correlationId: String? = null)
}
