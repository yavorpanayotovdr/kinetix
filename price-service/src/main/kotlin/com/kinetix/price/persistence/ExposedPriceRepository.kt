package com.kinetix.price.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
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

class ExposedPriceRepository(private val db: Database? = null) : PriceRepository {

    override suspend fun save(point: PricePoint): Unit = newSuspendedTransaction(db = db) {
        PriceTable.insert {
            it[instrumentId] = point.instrumentId.value
            it[priceAmount] = point.price.amount
            it[priceCurrency] = point.price.currency.currencyCode
            it[timestamp] = point.timestamp.atOffset(ZoneOffset.UTC)
            it[dataSource] = point.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(instrumentId: InstrumentId): PricePoint? =
        newSuspendedTransaction(db = db) {
            PriceTable
                .selectAll()
                .where { PriceTable.instrumentId eq instrumentId.value }
                .orderBy(PriceTable.timestamp, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toPricePoint()
        }

    override suspend fun findByInstrumentId(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<PricePoint> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        PriceTable
            .selectAll()
            .where {
                (PriceTable.instrumentId eq instrumentId.value) and
                    (PriceTable.timestamp greaterEq fromOffset) and
                    (PriceTable.timestamp lessEq toOffset)
            }
            .orderBy(PriceTable.timestamp, SortOrder.ASC)
            .map { it.toPricePoint() }
    }

    private fun ResultRow.toPricePoint(): PricePoint = PricePoint(
        instrumentId = InstrumentId(this[PriceTable.instrumentId]),
        price = Money(
            this[PriceTable.priceAmount],
            Currency.getInstance(this[PriceTable.priceCurrency]),
        ),
        timestamp = this[PriceTable.timestamp].toInstant(),
        source = PriceSource.valueOf(this[PriceTable.dataSource]),
    )
}
