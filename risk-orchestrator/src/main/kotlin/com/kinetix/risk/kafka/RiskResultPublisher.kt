package com.kinetix.risk.kafka

import com.kinetix.risk.model.VaRResult

interface RiskResultPublisher {
    suspend fun publish(result: VaRResult)
}
