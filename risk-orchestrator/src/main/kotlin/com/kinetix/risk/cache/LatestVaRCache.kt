package com.kinetix.risk.cache

import com.kinetix.risk.model.ValuationResult
import java.util.concurrent.ConcurrentHashMap

class LatestVaRCache {
    private val cache = ConcurrentHashMap<String, ValuationResult>()

    fun put(portfolioId: String, result: ValuationResult) {
        cache[portfolioId] = result
    }

    fun get(portfolioId: String): ValuationResult? = cache[portfolioId]
}
