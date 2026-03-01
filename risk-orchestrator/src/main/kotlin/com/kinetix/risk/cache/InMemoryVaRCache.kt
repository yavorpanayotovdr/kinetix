package com.kinetix.risk.cache

import com.kinetix.risk.model.ValuationResult
import java.util.concurrent.ConcurrentHashMap

class InMemoryVaRCache : VaRCache {
    private val cache = ConcurrentHashMap<String, ValuationResult>()

    override fun put(portfolioId: String, result: ValuationResult) {
        cache[portfolioId] = result
    }

    override fun get(portfolioId: String): ValuationResult? = cache[portfolioId]
}
