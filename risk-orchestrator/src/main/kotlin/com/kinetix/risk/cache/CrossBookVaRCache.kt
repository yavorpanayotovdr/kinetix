package com.kinetix.risk.cache

import com.kinetix.risk.model.CrossBookValuationResult

interface CrossBookVaRCache {
    fun put(groupId: String, result: CrossBookValuationResult)
    fun get(groupId: String): CrossBookValuationResult?
}
