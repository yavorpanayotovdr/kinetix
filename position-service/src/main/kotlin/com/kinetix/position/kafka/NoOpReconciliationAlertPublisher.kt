package com.kinetix.position.kafka

import com.kinetix.position.fix.PrimeBrokerReconciliation

class NoOpReconciliationAlertPublisher : ReconciliationAlertPublisher {
    override suspend fun publishBreakAlert(reconciliation: PrimeBrokerReconciliation) {
        // No-op for tests and environments without Kafka configured
    }
}
