package com.kinetix.position.persistence

import com.kinetix.common.model.BookId
import com.kinetix.position.model.StrategyType
import com.kinetix.position.model.TradeStrategy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.ZoneOffset

class ExposedTradeStrategyRepository(private val db: Database? = null) : TradeStrategyRepository {

    override suspend fun save(strategy: TradeStrategy): Unit = newSuspendedTransaction(db = db) {
        TradeStrategiesTable.insert {
            it[strategyId] = strategy.strategyId
            it[bookId] = strategy.bookId.value
            it[strategyType] = strategy.strategyType.name
            it[name] = strategy.name
            it[createdAt] = strategy.createdAt.atOffset(ZoneOffset.UTC)
        }
    }

    override suspend fun findByBookId(bookId: BookId): List<TradeStrategy> = newSuspendedTransaction(db = db) {
        TradeStrategiesTable
            .selectAll()
            .where { TradeStrategiesTable.bookId eq bookId.value }
            .orderBy(TradeStrategiesTable.createdAt)
            .map { it.toStrategy() }
    }

    override suspend fun findById(strategyId: String): TradeStrategy? = newSuspendedTransaction(db = db) {
        TradeStrategiesTable
            .selectAll()
            .where { TradeStrategiesTable.strategyId eq strategyId }
            .singleOrNull()
            ?.toStrategy()
    }

    private fun ResultRow.toStrategy() = TradeStrategy(
        strategyId = this[TradeStrategiesTable.strategyId],
        bookId = BookId(this[TradeStrategiesTable.bookId]),
        strategyType = StrategyType.valueOf(this[TradeStrategiesTable.strategyType]),
        name = this[TradeStrategiesTable.name],
        createdAt = this[TradeStrategiesTable.createdAt].toInstant(),
    )
}
