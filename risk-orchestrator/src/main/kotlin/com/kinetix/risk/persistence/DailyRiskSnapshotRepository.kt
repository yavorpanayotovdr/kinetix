package com.kinetix.risk.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.DailyRiskSnapshot
import java.time.LocalDate

interface DailyRiskSnapshotRepository {
    suspend fun save(snapshot: DailyRiskSnapshot)
    suspend fun saveAll(snapshots: List<DailyRiskSnapshot>)
    suspend fun findByPortfolioIdAndDate(portfolioId: PortfolioId, date: LocalDate): List<DailyRiskSnapshot>
    suspend fun findByPortfolioId(portfolioId: PortfolioId): List<DailyRiskSnapshot>
    suspend fun deleteByPortfolioIdAndDate(portfolioId: PortfolioId, date: LocalDate)
}
