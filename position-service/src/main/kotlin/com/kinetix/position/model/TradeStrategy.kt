package com.kinetix.position.model

import com.kinetix.common.model.BookId
import java.time.Instant

data class TradeStrategy(
    val strategyId: String,
    val bookId: BookId,
    val strategyType: StrategyType,
    val name: String?,
    val createdAt: Instant,
)
