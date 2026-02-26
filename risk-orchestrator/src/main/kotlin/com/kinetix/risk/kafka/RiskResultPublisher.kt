package com.kinetix.risk.kafka

import com.kinetix.risk.model.ValuationResult

interface RiskResultPublisher {
    suspend fun publish(result: ValuationResult)
}
