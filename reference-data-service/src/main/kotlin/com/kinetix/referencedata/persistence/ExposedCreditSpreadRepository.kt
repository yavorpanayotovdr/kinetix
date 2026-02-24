package com.kinetix.referencedata.persistence

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedCreditSpreadRepository(private val db: Database? = null) : CreditSpreadRepository {

    override suspend fun save(creditSpread: CreditSpread): Unit = newSuspendedTransaction(db = db) {
        CreditSpreadTable.insert {
            it[instrumentId] = creditSpread.instrumentId.value
            it[asOfDate] = creditSpread.asOfDate.atOffset(ZoneOffset.UTC)
            it[spread] = creditSpread.spread.toBigDecimal()
            it[rating] = creditSpread.rating
            it[dataSource] = creditSpread.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(instrumentId: InstrumentId): CreditSpread? =
        newSuspendedTransaction(db = db) {
            CreditSpreadTable
                .selectAll()
                .where { CreditSpreadTable.instrumentId eq instrumentId.value }
                .orderBy(CreditSpreadTable.asOfDate, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toCreditSpread()
        }

    override suspend fun findByTimeRange(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<CreditSpread> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        CreditSpreadTable
            .selectAll()
            .where {
                (CreditSpreadTable.instrumentId eq instrumentId.value) and
                    (CreditSpreadTable.asOfDate greaterEq fromOffset) and
                    (CreditSpreadTable.asOfDate lessEq toOffset)
            }
            .orderBy(CreditSpreadTable.asOfDate, SortOrder.ASC)
            .map { it.toCreditSpread() }
    }

    private fun ResultRow.toCreditSpread(): CreditSpread = CreditSpread(
        instrumentId = InstrumentId(this[CreditSpreadTable.instrumentId]),
        spread = this[CreditSpreadTable.spread].toDouble(),
        rating = this[CreditSpreadTable.rating],
        asOfDate = this[CreditSpreadTable.asOfDate].toInstant(),
        source = ReferenceDataSource.valueOf(this[CreditSpreadTable.dataSource]),
    )
}
