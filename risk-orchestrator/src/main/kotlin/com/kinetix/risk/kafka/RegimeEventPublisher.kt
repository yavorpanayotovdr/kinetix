package com.kinetix.risk.kafka

import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeState

interface RegimeEventPublisher {
    /**
     * Publish a regime transition event to the `risk.regime.changes` Kafka topic.
     *
     * Called by [ScheduledRegimeDetector] after a confirmed transition.
     * The [previousRegime] is the regime that was active before this transition.
     */
    suspend fun publish(from: MarketRegime, to: RegimeState, correlationId: String? = null)
}
