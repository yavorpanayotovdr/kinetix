package com.kinetix.risk.service

import com.kinetix.risk.model.RiskAuditEvent

interface RiskAuditEventPublisher {
    suspend fun publish(event: RiskAuditEvent)
}
