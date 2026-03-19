package com.kinetix.position.persistence

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Trade
import com.kinetix.common.model.TradeId
import com.kinetix.common.model.TradeStatus
import java.time.Instant

interface TradeEventRepository {
    suspend fun save(trade: Trade)
    suspend fun findByTradeId(tradeId: TradeId): Trade?
    suspend fun findByBookId(portfolioId: BookId): List<Trade>
    suspend fun updateStatus(tradeId: TradeId, status: TradeStatus)
    suspend fun countSince(since: Instant): Long
}
