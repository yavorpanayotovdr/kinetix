package com.kinetix.position.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position

interface PositionRepository {
    suspend fun save(position: Position)
    suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Position>
    suspend fun findByKey(portfolioId: PortfolioId, instrumentId: InstrumentId): Position?
    suspend fun delete(portfolioId: PortfolioId, instrumentId: InstrumentId)
}
