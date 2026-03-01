package com.kinetix.risk.cache

import com.kinetix.risk.model.ValuationResult

interface VaRCache {
    fun put(portfolioId: String, result: ValuationResult)
    fun get(portfolioId: String): ValuationResult?
}
