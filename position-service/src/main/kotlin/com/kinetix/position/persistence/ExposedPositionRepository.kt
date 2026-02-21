package com.kinetix.position.persistence

import com.kinetix.common.model.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedPositionRepository(private val db: Database? = null) : PositionRepository {

    override suspend fun save(position: Position): Unit = newSuspendedTransaction(db = db) {
        PositionsTable.upsert(PositionsTable.portfolioId, PositionsTable.instrumentId) {
            it[portfolioId] = position.portfolioId.value
            it[instrumentId] = position.instrumentId.value
            it[assetClass] = position.assetClass.name
            it[quantity] = position.quantity
            it[avgCostAmount] = position.averageCost.amount
            it[marketPriceAmount] = position.marketPrice.amount
            it[currency] = position.currency.currencyCode
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Position> = newSuspendedTransaction(db = db) {
        PositionsTable
            .selectAll()
            .where { PositionsTable.portfolioId eq portfolioId.value }
            .map { it.toPosition() }
    }

    override suspend fun findByInstrumentId(instrumentId: InstrumentId): List<Position> = newSuspendedTransaction(db = db) {
        PositionsTable
            .selectAll()
            .where { PositionsTable.instrumentId eq instrumentId.value }
            .map { it.toPosition() }
    }

    override suspend fun findByKey(
        portfolioId: PortfolioId,
        instrumentId: InstrumentId,
    ): Position? = newSuspendedTransaction(db = db) {
        PositionsTable
            .selectAll()
            .where {
                (PositionsTable.portfolioId eq portfolioId.value) and
                    (PositionsTable.instrumentId eq instrumentId.value)
            }
            .singleOrNull()
            ?.toPosition()
    }

    override suspend fun delete(
        portfolioId: PortfolioId,
        instrumentId: InstrumentId,
    ): Unit = newSuspendedTransaction(db = db) {
        PositionsTable.deleteWhere {
            (PositionsTable.portfolioId eq portfolioId.value) and
                (PositionsTable.instrumentId eq instrumentId.value)
        }
    }

    override suspend fun findDistinctPortfolioIds(): List<PortfolioId> = newSuspendedTransaction(db = db) {
        PositionsTable
            .select(PositionsTable.portfolioId)
            .withDistinct()
            .map { PortfolioId(it[PositionsTable.portfolioId]) }
    }

    private fun ResultRow.toPosition(): Position = Position(
        portfolioId = PortfolioId(this[PositionsTable.portfolioId]),
        instrumentId = InstrumentId(this[PositionsTable.instrumentId]),
        assetClass = AssetClass.valueOf(this[PositionsTable.assetClass]),
        quantity = this[PositionsTable.quantity],
        averageCost = Money(
            this[PositionsTable.avgCostAmount],
            Currency.getInstance(this[PositionsTable.currency]),
        ),
        marketPrice = Money(
            this[PositionsTable.marketPriceAmount],
            Currency.getInstance(this[PositionsTable.currency]),
        ),
    )
}
