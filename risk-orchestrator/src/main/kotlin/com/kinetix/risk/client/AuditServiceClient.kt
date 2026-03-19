package com.kinetix.risk.client

import java.time.Instant

interface AuditServiceClient {
    suspend fun countAuditEventsSince(since: Instant): ClientResponse<Long>
}
