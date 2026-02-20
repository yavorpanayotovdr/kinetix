package com.kinetix.marketdata.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedMarketDataRepository(private val db: Database? = null) : MarketDataRepository {

    override suspend fun save(point: MarketDataPoint): Unit = newSuspendedTransaction(db = db) {
        MarketDataTable.insert {
            it[instrumentId] = point.instrumentId.value
            it[priceAmount] = point.price.amount
            it[priceCurrency] = point.price.currency.currencyCode
            it[timestamp] = point.timestamp.atOffset(ZoneOffset.UTC)
            it[dataSource] = point.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(instrumentId: InstrumentId): MarketDataPoint? =
        newSuspendedTransaction(db = db) {
            MarketDataTable
                .selectAll()
                .where { MarketDataTable.instrumentId eq instrumentId.value }
                .orderBy(MarketDataTable.timestamp, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toMarketDataPoint()
        }

    override suspend fun findByInstrumentId(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<MarketDataPoint> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        MarketDataTable
            .selectAll()
            .where {
                (MarketDataTable.instrumentId eq instrumentId.value) and
                    (MarketDataTable.timestamp greaterEq fromOffset) and
                    (MarketDataTable.timestamp lessEq toOffset)
            }
            .orderBy(MarketDataTable.timestamp, SortOrder.ASC)
            .map { it.toMarketDataPoint() }
    }

    private fun ResultRow.toMarketDataPoint(): MarketDataPoint = MarketDataPoint(
        instrumentId = InstrumentId(this[MarketDataTable.instrumentId]),
        price = Money(
            this[MarketDataTable.priceAmount],
            Currency.getInstance(this[MarketDataTable.priceCurrency]),
        ),
        timestamp = this[MarketDataTable.timestamp].toInstant(),
        source = MarketDataSource.valueOf(this[MarketDataTable.dataSource]),
    )
}
