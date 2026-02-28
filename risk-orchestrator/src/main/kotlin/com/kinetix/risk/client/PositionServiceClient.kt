package com.kinetix.risk.client

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position

interface PositionServiceClient {
    suspend fun getPositions(portfolioId: PortfolioId): ClientResponse<List<Position>>
    suspend fun getDistinctPortfolioIds(): ClientResponse<List<PortfolioId>>
}
