package com.kinetix.position.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Trade
import com.kinetix.common.model.TradeId

interface TradeEventRepository {
    suspend fun save(trade: Trade)
    suspend fun findByTradeId(tradeId: TradeId): Trade?
    suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Trade>
}
