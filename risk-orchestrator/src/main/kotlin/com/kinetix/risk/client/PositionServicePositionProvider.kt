package com.kinetix.risk.client

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.position.persistence.PositionRepository

class PositionServicePositionProvider(
    private val positionRepository: PositionRepository,
) : PositionProvider {

    override suspend fun getPositions(portfolioId: PortfolioId): List<Position> {
        return positionRepository.findByPortfolioId(portfolioId)
    }
}
