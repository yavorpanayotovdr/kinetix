package com.kinetix.risk.kafka

import com.kinetix.risk.model.RiskAuditEvent
import com.kinetix.risk.service.RiskAuditEventPublisher

class NoOpRiskAuditEventPublisher : RiskAuditEventPublisher {
    override suspend fun publish(event: RiskAuditEvent) { /* no-op */ }
}
