package com.kinetix.referencedata.persistence

import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedDividendYieldRepository(private val db: Database? = null) : DividendYieldRepository {

    override suspend fun save(dividendYield: DividendYield): Unit = newSuspendedTransaction(db = db) {
        DividendYieldTable.insert {
            it[instrumentId] = dividendYield.instrumentId.value
            it[asOfDate] = dividendYield.asOfDate.atOffset(ZoneOffset.UTC)
            it[yield_] = dividendYield.yield.toBigDecimal()
            it[exDate] = dividendYield.exDate?.toString()
            it[dataSource] = dividendYield.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(instrumentId: InstrumentId): DividendYield? =
        newSuspendedTransaction(db = db) {
            DividendYieldTable
                .selectAll()
                .where { DividendYieldTable.instrumentId eq instrumentId.value }
                .orderBy(DividendYieldTable.asOfDate, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toDividendYield()
        }

    override suspend fun findByTimeRange(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<DividendYield> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        DividendYieldTable
            .selectAll()
            .where {
                (DividendYieldTable.instrumentId eq instrumentId.value) and
                    (DividendYieldTable.asOfDate greaterEq fromOffset) and
                    (DividendYieldTable.asOfDate lessEq toOffset)
            }
            .orderBy(DividendYieldTable.asOfDate, SortOrder.ASC)
            .map { it.toDividendYield() }
    }

    private fun ResultRow.toDividendYield(): DividendYield = DividendYield(
        instrumentId = InstrumentId(this[DividendYieldTable.instrumentId]),
        yield = this[DividendYieldTable.yield_].toDouble(),
        exDate = this[DividendYieldTable.exDate]?.let { LocalDate.parse(it) },
        asOfDate = this[DividendYieldTable.asOfDate].toInstant(),
        source = ReferenceDataSource.valueOf(this[DividendYieldTable.dataSource]),
    )
}
