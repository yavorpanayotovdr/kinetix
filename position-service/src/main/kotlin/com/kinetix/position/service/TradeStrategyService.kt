package com.kinetix.position.service

import com.kinetix.common.model.BookId
import com.kinetix.position.model.StrategyType
import com.kinetix.position.model.TradeStrategy
import com.kinetix.position.persistence.TradeStrategyRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TradeStrategyService(
    private val strategyRepository: TradeStrategyRepository,
) {

    suspend fun createStrategy(
        bookId: BookId,
        strategyType: StrategyType,
        name: String?,
    ): TradeStrategy {
        val strategy = TradeStrategy(
            strategyId = UUID.randomUUID().toString(),
            bookId = bookId,
            strategyType = strategyType,
            name = name,
            createdAt = Instant.now(),
        )
        strategyRepository.save(strategy)
        return strategy
    }

    suspend fun listStrategies(bookId: BookId): List<TradeStrategy> =
        strategyRepository.findByBookId(bookId)

    suspend fun findById(strategyId: String): TradeStrategy? =
        strategyRepository.findById(strategyId)

    fun aggregateGreeks(legs: List<LegGreeks>): AggregatedGreeks = AggregatedGreeks(
        deltaNet = legs.fold(BigDecimal.ZERO) { acc, g -> acc + g.delta },
        gammaNet = legs.fold(BigDecimal.ZERO) { acc, g -> acc + g.gamma },
        vegaNet = legs.fold(BigDecimal.ZERO) { acc, g -> acc + g.vega },
        thetaNet = legs.fold(BigDecimal.ZERO) { acc, g -> acc + g.theta },
    )

    fun aggregatePnl(pnls: List<BigDecimal>): BigDecimal =
        pnls.fold(BigDecimal.ZERO, BigDecimal::add)
}
