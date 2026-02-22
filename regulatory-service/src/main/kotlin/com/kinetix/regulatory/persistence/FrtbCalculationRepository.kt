package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.FrtbCalculationRecord

interface FrtbCalculationRepository {
    suspend fun save(record: FrtbCalculationRecord)
    suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int): List<FrtbCalculationRecord>
    suspend fun findLatestByPortfolioId(portfolioId: String): FrtbCalculationRecord?
}
