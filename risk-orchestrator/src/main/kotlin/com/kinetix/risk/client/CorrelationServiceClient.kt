package com.kinetix.risk.client

import com.kinetix.common.model.CorrelationMatrix

interface CorrelationServiceClient {
    suspend fun getCorrelationMatrix(labels: List<String>, windowDays: Int = 252): CorrelationMatrix?
}
