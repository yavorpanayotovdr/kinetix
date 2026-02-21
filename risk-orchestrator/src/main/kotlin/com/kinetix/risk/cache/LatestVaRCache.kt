package com.kinetix.risk.cache

import com.kinetix.risk.model.VaRResult
import java.util.concurrent.ConcurrentHashMap

class LatestVaRCache {
    private val cache = ConcurrentHashMap<String, VaRResult>()

    fun put(portfolioId: String, result: VaRResult) {
        cache[portfolioId] = result
    }

    fun get(portfolioId: String): VaRResult? = cache[portfolioId]
}
