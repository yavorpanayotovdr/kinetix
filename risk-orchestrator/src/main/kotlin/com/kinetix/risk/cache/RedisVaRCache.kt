package com.kinetix.risk.cache

import com.kinetix.risk.model.ValuationResult
import io.lettuce.core.api.StatefulRedisConnection

class RedisVaRCache(
    private val connection: StatefulRedisConnection<String, String>,
    private val ttlSeconds: Long = 300L,
) : VaRCache {

    override fun put(portfolioId: String, result: ValuationResult) {
        TODO("Not yet implemented")
    }

    override fun get(portfolioId: String): ValuationResult? {
        TODO("Not yet implemented")
    }
}
