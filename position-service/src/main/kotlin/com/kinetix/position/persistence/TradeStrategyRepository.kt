package com.kinetix.position.persistence

import com.kinetix.common.model.BookId
import com.kinetix.position.model.TradeStrategy

interface TradeStrategyRepository {
    suspend fun save(strategy: TradeStrategy)
    suspend fun findByBookId(bookId: BookId): List<TradeStrategy>
    suspend fun findById(strategyId: String): TradeStrategy?
}
