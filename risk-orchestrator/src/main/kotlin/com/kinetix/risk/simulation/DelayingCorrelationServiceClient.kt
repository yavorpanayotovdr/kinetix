package com.kinetix.risk.simulation

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.risk.client.CorrelationServiceClient
import kotlinx.coroutines.delay

class DelayingCorrelationServiceClient(
    private val delegate: CorrelationServiceClient,
    private val delayMs: LongRange,
) : CorrelationServiceClient {

    override suspend fun getCorrelationMatrix(labels: List<String>, windowDays: Int): CorrelationMatrix? {
        delay(delayMs.random())
        return delegate.getCorrelationMatrix(labels, windowDays)
    }
}
