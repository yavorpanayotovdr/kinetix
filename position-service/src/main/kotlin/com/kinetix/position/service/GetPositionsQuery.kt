package com.kinetix.position.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.position.persistence.PositionRepository

data class GetPositionsQuery(
    val portfolioId: PortfolioId,
)

class PositionQueryService(
    private val positionRepository: PositionRepository,
) {
    suspend fun handle(query: GetPositionsQuery): List<Position> {
        return positionRepository.findByPortfolioId(query.portfolioId)
    }
}
