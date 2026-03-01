package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.BacktestResultRecord

interface BacktestResultRepository {
    suspend fun save(record: BacktestResultRecord)
    suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int): List<BacktestResultRecord>
    suspend fun findLatestByPortfolioId(portfolioId: String): BacktestResultRecord?
}
