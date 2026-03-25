package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.DailyRiskSnapshot
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedDailyRiskSnapshotRepository(private val db: Database? = null) : DailyRiskSnapshotRepository {

    override suspend fun save(snapshot: DailyRiskSnapshot): Unit = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable.upsert(
            DailyRiskSnapshotsTable.bookId,
            DailyRiskSnapshotsTable.snapshotDate,
            DailyRiskSnapshotsTable.instrumentId,
        ) {
            it[bookId] = snapshot.bookId.value
            it[snapshotDate] = snapshot.snapshotDate.toMidnightUtc()
            it[instrumentId] = snapshot.instrumentId.value
            it[assetClass] = snapshot.assetClass.name
            it[quantity] = snapshot.quantity
            it[marketPrice] = snapshot.marketPrice
            it[delta] = snapshot.delta
            it[gamma] = snapshot.gamma
            it[vega] = snapshot.vega
            it[theta] = snapshot.theta
            it[rho] = snapshot.rho
            it[varContribution] = snapshot.varContribution
            it[esContribution] = snapshot.esContribution
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun saveAll(snapshots: List<DailyRiskSnapshot>): Unit = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable.batchUpsert(
            snapshots,
            DailyRiskSnapshotsTable.bookId,
            DailyRiskSnapshotsTable.snapshotDate,
            DailyRiskSnapshotsTable.instrumentId,
            shouldReturnGeneratedValues = false,
        ) { snapshot ->
            this[DailyRiskSnapshotsTable.bookId] = snapshot.bookId.value
            this[DailyRiskSnapshotsTable.snapshotDate] = snapshot.snapshotDate.toMidnightUtc()
            this[DailyRiskSnapshotsTable.instrumentId] = snapshot.instrumentId.value
            this[DailyRiskSnapshotsTable.assetClass] = snapshot.assetClass.name
            this[DailyRiskSnapshotsTable.quantity] = snapshot.quantity
            this[DailyRiskSnapshotsTable.marketPrice] = snapshot.marketPrice
            this[DailyRiskSnapshotsTable.delta] = snapshot.delta
            this[DailyRiskSnapshotsTable.gamma] = snapshot.gamma
            this[DailyRiskSnapshotsTable.vega] = snapshot.vega
            this[DailyRiskSnapshotsTable.theta] = snapshot.theta
            this[DailyRiskSnapshotsTable.rho] = snapshot.rho
            this[DailyRiskSnapshotsTable.varContribution] = snapshot.varContribution
            this[DailyRiskSnapshotsTable.esContribution] = snapshot.esContribution
            this[DailyRiskSnapshotsTable.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByBookIdAndDate(
        bookId: BookId,
        date: LocalDate,
    ): List<DailyRiskSnapshot> = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable
            .selectAll()
            .where {
                (DailyRiskSnapshotsTable.bookId eq bookId.value) and
                    (DailyRiskSnapshotsTable.snapshotDate eq date.toMidnightUtc())
            }
            .orderBy(DailyRiskSnapshotsTable.instrumentId, SortOrder.ASC)
            .map { it.toDailyRiskSnapshot() }
    }

    override suspend fun findByBookId(
        bookId: BookId,
        fromDate: LocalDate,
    ): List<DailyRiskSnapshot> = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable
            .selectAll()
            .where {
                (DailyRiskSnapshotsTable.bookId eq bookId.value) and
                    (DailyRiskSnapshotsTable.snapshotDate greaterEq fromDate.toMidnightUtc())
            }
            .orderBy(DailyRiskSnapshotsTable.snapshotDate, SortOrder.DESC)
            .map { it.toDailyRiskSnapshot() }
    }

    override suspend fun deleteByBookIdAndDate(
        bookId: BookId,
        date: LocalDate,
    ): Unit = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable.deleteWhere {
            (DailyRiskSnapshotsTable.bookId eq bookId.value) and
                (DailyRiskSnapshotsTable.snapshotDate eq date.toMidnightUtc())
        }
    }

    private fun ResultRow.toDailyRiskSnapshot(): DailyRiskSnapshot = DailyRiskSnapshot(
        id = this[DailyRiskSnapshotsTable.id],
        bookId = BookId(this[DailyRiskSnapshotsTable.bookId]),
        snapshotDate = this[DailyRiskSnapshotsTable.snapshotDate].toLocalDate(),
        instrumentId = InstrumentId(this[DailyRiskSnapshotsTable.instrumentId]),
        assetClass = AssetClass.valueOf(this[DailyRiskSnapshotsTable.assetClass]),
        quantity = this[DailyRiskSnapshotsTable.quantity],
        marketPrice = this[DailyRiskSnapshotsTable.marketPrice],
        delta = this[DailyRiskSnapshotsTable.delta],
        gamma = this[DailyRiskSnapshotsTable.gamma],
        vega = this[DailyRiskSnapshotsTable.vega],
        theta = this[DailyRiskSnapshotsTable.theta],
        rho = this[DailyRiskSnapshotsTable.rho],
        varContribution = this[DailyRiskSnapshotsTable.varContribution],
        esContribution = this[DailyRiskSnapshotsTable.esContribution],
    )
}

internal fun LocalDate.toMidnightUtc(): OffsetDateTime =
    OffsetDateTime.of(this, LocalTime.MIDNIGHT, ZoneOffset.UTC)
