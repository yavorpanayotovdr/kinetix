package com.kinetix.risk.kafka

import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeState

class NoOpRegimeEventPublisher : RegimeEventPublisher {
    override suspend fun publish(from: MarketRegime, to: RegimeState, correlationId: String?) = Unit
}
