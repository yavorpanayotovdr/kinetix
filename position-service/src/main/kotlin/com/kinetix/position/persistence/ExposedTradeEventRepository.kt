package com.kinetix.position.persistence

import com.kinetix.common.model.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedTradeEventRepository(private val db: Database? = null) : TradeEventRepository {

    override suspend fun save(trade: Trade): Unit = newSuspendedTransaction(db = db) {
        TradeEventsTable.insert {
            it[tradeId] = trade.tradeId.value
            it[bookId] = trade.bookId.value
            it[instrumentId] = trade.instrumentId.value
            it[assetClass] = trade.assetClass.name
            it[side] = trade.side.name
            it[quantity] = trade.quantity
            it[priceAmount] = trade.price.amount
            it[priceCurrency] = trade.price.currency.currencyCode
            it[tradedAt] = trade.tradedAt.atOffset(ZoneOffset.UTC)
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[eventType] = trade.eventType.name
            it[status] = trade.status.name
            it[originalTradeId] = trade.originalTradeId?.value
            it[counterpartyId] = trade.counterpartyId
            it[instrumentType] = trade.instrumentType ?: "UNKNOWN"
            it[strategyId] = trade.strategyId
        }
    }

    override suspend fun findByTradeId(tradeId: TradeId): Trade? = newSuspendedTransaction(db = db) {
        TradeEventsTable
            .selectAll()
            .where { TradeEventsTable.tradeId eq tradeId.value }
            .singleOrNull()
            ?.toTrade()
    }

    override suspend fun findByBookId(bookId: BookId): List<Trade> = newSuspendedTransaction(db = db) {
        TradeEventsTable
            .selectAll()
            .where { TradeEventsTable.bookId eq bookId.value }
            .orderBy(TradeEventsTable.tradedAt)
            .map { it.toTrade() }
    }

    override suspend fun updateStatus(tradeId: TradeId, status: TradeStatus): Unit = newSuspendedTransaction(db = db) {
        TradeEventsTable.update({ TradeEventsTable.tradeId eq tradeId.value }) {
            it[TradeEventsTable.status] = status.name
        }
    }

    override suspend fun countSince(since: Instant): Long = newSuspendedTransaction(db = db) {
        TradeEventsTable
            .selectAll()
            .where { TradeEventsTable.createdAt greaterEq since.atOffset(ZoneOffset.UTC) }
            .count()
    }

    private fun ResultRow.toTrade(): Trade = Trade(
        tradeId = TradeId(this[TradeEventsTable.tradeId]),
        bookId = BookId(this[TradeEventsTable.bookId]),
        instrumentId = InstrumentId(this[TradeEventsTable.instrumentId]),
        assetClass = AssetClass.valueOf(this[TradeEventsTable.assetClass]),
        side = Side.valueOf(this[TradeEventsTable.side]),
        quantity = this[TradeEventsTable.quantity],
        price = Money(
            this[TradeEventsTable.priceAmount],
            Currency.getInstance(this[TradeEventsTable.priceCurrency]),
        ),
        tradedAt = this[TradeEventsTable.tradedAt].toInstant(),
        eventType = TradeEventType.valueOf(this[TradeEventsTable.eventType]),
        status = TradeStatus.valueOf(this[TradeEventsTable.status]),
        originalTradeId = this[TradeEventsTable.originalTradeId]?.let { TradeId(it) },
        counterpartyId = this[TradeEventsTable.counterpartyId],
        instrumentType = this[TradeEventsTable.instrumentType],
        strategyId = this[TradeEventsTable.strategyId],
    )
}
