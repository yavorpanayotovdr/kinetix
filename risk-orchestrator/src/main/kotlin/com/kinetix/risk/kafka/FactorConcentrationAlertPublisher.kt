package com.kinetix.risk.kafka

import com.kinetix.risk.model.FactorDecompositionSnapshot

/**
 * Publishes an alert when factor concentration risk is detected.
 * Implementations send this downstream so that the notification-service
 * can fire a FACTOR_CONCENTRATION alert to configured subscribers.
 */
interface FactorConcentrationAlertPublisher {
    suspend fun publishConcentrationWarning(snapshot: FactorDecompositionSnapshot)
}
