package com.kinetix.risk.client

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position

interface PositionProvider {
    suspend fun getPositions(portfolioId: PortfolioId): List<Position>
}
