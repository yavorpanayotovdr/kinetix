package com.kinetix.risk.simulation

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.PositionProvider
import kotlinx.coroutines.delay

class DelayingPositionProvider(
    private val delegate: PositionProvider,
    private val delayMs: LongRange,
) : PositionProvider {

    override suspend fun getPositions(portfolioId: PortfolioId): List<Position> {
        delay(delayMs.random())
        return delegate.getPositions(portfolioId)
    }
}
