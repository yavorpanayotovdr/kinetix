package com.kinetix.risk.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SodBaseline
import java.time.LocalDate

interface SodBaselineRepository {
    suspend fun save(baseline: SodBaseline)
    suspend fun findByPortfolioIdAndDate(portfolioId: PortfolioId, date: LocalDate): SodBaseline?
    suspend fun deleteByPortfolioIdAndDate(portfolioId: PortfolioId, date: LocalDate)
}
