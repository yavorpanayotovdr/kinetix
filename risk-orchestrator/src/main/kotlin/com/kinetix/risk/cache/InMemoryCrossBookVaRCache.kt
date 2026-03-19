package com.kinetix.risk.cache

import com.kinetix.risk.model.CrossBookValuationResult
import java.util.concurrent.ConcurrentHashMap

class InMemoryCrossBookVaRCache : CrossBookVaRCache {
    private val cache = ConcurrentHashMap<String, CrossBookValuationResult>()

    override fun put(groupId: String, result: CrossBookValuationResult) {
        cache[groupId] = result
    }

    override fun get(groupId: String): CrossBookValuationResult? = cache[groupId]
}
