package com.kinetix.risk.client

import java.time.Instant

interface PositionServiceInternalClient {
    suspend fun countTradeEventsSince(since: Instant): ClientResponse<Long>
}
