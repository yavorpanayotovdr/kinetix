package com.kinetix.risk.service

import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.RegimeState

/**
 * Notified when a regime transition is confirmed.
 *
 * Implementations publish to Kafka, update Redis, write to the database, or send alerts.
 */
interface RegimeTransitionListener {
    suspend fun onRegimeTransition(from: MarketRegime, to: MarketRegime, state: RegimeState)
}
