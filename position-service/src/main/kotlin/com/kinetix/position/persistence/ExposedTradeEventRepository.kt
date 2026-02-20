package com.kinetix.position.persistence

import com.kinetix.common.model.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedTradeEventRepository : TradeEventRepository {

    override suspend fun save(trade: Trade): Unit = newSuspendedTransaction {
        TradeEventsTable.insert {
            it[tradeId] = trade.tradeId.value
            it[portfolioId] = trade.portfolioId.value
            it[instrumentId] = trade.instrumentId.value
            it[assetClass] = trade.assetClass.name
            it[side] = trade.side.name
            it[quantity] = trade.quantity
            it[priceAmount] = trade.price.amount
            it[priceCurrency] = trade.price.currency.currencyCode
            it[tradedAt] = trade.tradedAt.atOffset(ZoneOffset.UTC)
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByTradeId(tradeId: TradeId): Trade? = newSuspendedTransaction {
        TradeEventsTable
            .selectAll()
            .where { TradeEventsTable.tradeId eq tradeId.value }
            .singleOrNull()
            ?.toTrade()
    }

    override suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Trade> = newSuspendedTransaction {
        TradeEventsTable
            .selectAll()
            .where { TradeEventsTable.portfolioId eq portfolioId.value }
            .orderBy(TradeEventsTable.tradedAt)
            .map { it.toTrade() }
    }

    private fun ResultRow.toTrade(): Trade = Trade(
        tradeId = TradeId(this[TradeEventsTable.tradeId]),
        portfolioId = PortfolioId(this[TradeEventsTable.portfolioId]),
        instrumentId = InstrumentId(this[TradeEventsTable.instrumentId]),
        assetClass = AssetClass.valueOf(this[TradeEventsTable.assetClass]),
        side = Side.valueOf(this[TradeEventsTable.side]),
        quantity = this[TradeEventsTable.quantity],
        price = Money(
            this[TradeEventsTable.priceAmount],
            Currency.getInstance(this[TradeEventsTable.priceCurrency]),
        ),
        tradedAt = this[TradeEventsTable.tradedAt].toInstant(),
    )
}
