package com.kinetix.notification.audit

import com.kinetix.common.audit.GovernanceAuditEvent

interface GovernanceAuditPublisher {
    fun publish(event: GovernanceAuditEvent)
}
