package com.kinetix.risk.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PnlAttribution
import java.time.LocalDate

interface PnlAttributionRepository {
    suspend fun save(attribution: PnlAttribution)
    suspend fun findByPortfolioIdAndDate(portfolioId: PortfolioId, date: LocalDate): PnlAttribution?
    suspend fun findLatestByPortfolioId(portfolioId: PortfolioId): PnlAttribution?
    suspend fun findByPortfolioId(portfolioId: PortfolioId): List<PnlAttribution>
}
