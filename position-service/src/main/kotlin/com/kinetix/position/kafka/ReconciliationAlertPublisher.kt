package com.kinetix.position.kafka

import com.kinetix.position.fix.PrimeBrokerReconciliation

/**
 * Publishes an alert when a prime broker reconciliation finds critical breaks
 * (notional > $10,000).
 */
interface ReconciliationAlertPublisher {
    suspend fun publishBreakAlert(reconciliation: PrimeBrokerReconciliation)
}
