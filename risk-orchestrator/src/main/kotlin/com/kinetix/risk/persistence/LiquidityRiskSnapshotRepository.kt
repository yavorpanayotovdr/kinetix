package com.kinetix.risk.persistence

import com.kinetix.common.model.LiquidityRiskResult

interface LiquidityRiskSnapshotRepository {
    suspend fun save(result: LiquidityRiskResult)
    suspend fun findLatestByBookId(bookId: String): LiquidityRiskResult?
    suspend fun findAllByBookId(bookId: String, limit: Int = 100): List<LiquidityRiskResult>
}
