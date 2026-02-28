package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.DailyRiskSnapshot
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedDailyRiskSnapshotRepository(private val db: Database? = null) : DailyRiskSnapshotRepository {

    override suspend fun save(snapshot: DailyRiskSnapshot): Unit = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable.upsert(
            DailyRiskSnapshotsTable.portfolioId,
            DailyRiskSnapshotsTable.snapshotDate,
            DailyRiskSnapshotsTable.instrumentId,
        ) {
            it[portfolioId] = snapshot.portfolioId.value
            it[snapshotDate] = snapshot.snapshotDate.toKotlinxDate()
            it[instrumentId] = snapshot.instrumentId.value
            it[assetClass] = snapshot.assetClass.name
            it[quantity] = snapshot.quantity
            it[marketPrice] = snapshot.marketPrice
            it[delta] = snapshot.delta
            it[gamma] = snapshot.gamma
            it[vega] = snapshot.vega
            it[theta] = snapshot.theta
            it[rho] = snapshot.rho
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun saveAll(snapshots: List<DailyRiskSnapshot>): Unit = newSuspendedTransaction(db = db) {
        for (snapshot in snapshots) {
            DailyRiskSnapshotsTable.upsert(
                DailyRiskSnapshotsTable.portfolioId,
                DailyRiskSnapshotsTable.snapshotDate,
                DailyRiskSnapshotsTable.instrumentId,
            ) {
                it[portfolioId] = snapshot.portfolioId.value
                it[snapshotDate] = snapshot.snapshotDate.toKotlinxDate()
                it[instrumentId] = snapshot.instrumentId.value
                it[assetClass] = snapshot.assetClass.name
                it[quantity] = snapshot.quantity
                it[marketPrice] = snapshot.marketPrice
                it[delta] = snapshot.delta
                it[gamma] = snapshot.gamma
                it[vega] = snapshot.vega
                it[theta] = snapshot.theta
                it[rho] = snapshot.rho
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    override suspend fun findByPortfolioIdAndDate(
        portfolioId: PortfolioId,
        date: LocalDate,
    ): List<DailyRiskSnapshot> = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable
            .selectAll()
            .where {
                (DailyRiskSnapshotsTable.portfolioId eq portfolioId.value) and
                    (DailyRiskSnapshotsTable.snapshotDate eq date.toKotlinxDate())
            }
            .orderBy(DailyRiskSnapshotsTable.instrumentId, SortOrder.ASC)
            .map { it.toDailyRiskSnapshot() }
    }

    override suspend fun findByPortfolioId(
        portfolioId: PortfolioId,
    ): List<DailyRiskSnapshot> = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable
            .selectAll()
            .where { DailyRiskSnapshotsTable.portfolioId eq portfolioId.value }
            .orderBy(DailyRiskSnapshotsTable.snapshotDate, SortOrder.DESC)
            .map { it.toDailyRiskSnapshot() }
    }

    override suspend fun deleteByPortfolioIdAndDate(
        portfolioId: PortfolioId,
        date: LocalDate,
    ): Unit = newSuspendedTransaction(db = db) {
        DailyRiskSnapshotsTable.deleteWhere {
            (DailyRiskSnapshotsTable.portfolioId eq portfolioId.value) and
                (DailyRiskSnapshotsTable.snapshotDate eq date.toKotlinxDate())
        }
    }

    private fun ResultRow.toDailyRiskSnapshot(): DailyRiskSnapshot = DailyRiskSnapshot(
        id = this[DailyRiskSnapshotsTable.id],
        portfolioId = PortfolioId(this[DailyRiskSnapshotsTable.portfolioId]),
        snapshotDate = this[DailyRiskSnapshotsTable.snapshotDate].toJavaDate(),
        instrumentId = InstrumentId(this[DailyRiskSnapshotsTable.instrumentId]),
        assetClass = AssetClass.valueOf(this[DailyRiskSnapshotsTable.assetClass]),
        quantity = this[DailyRiskSnapshotsTable.quantity],
        marketPrice = this[DailyRiskSnapshotsTable.marketPrice],
        delta = this[DailyRiskSnapshotsTable.delta],
        gamma = this[DailyRiskSnapshotsTable.gamma],
        vega = this[DailyRiskSnapshotsTable.vega],
        theta = this[DailyRiskSnapshotsTable.theta],
        rho = this[DailyRiskSnapshotsTable.rho],
    )
}

internal fun LocalDate.toKotlinxDate(): kotlinx.datetime.LocalDate =
    kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

internal fun kotlinx.datetime.LocalDate.toJavaDate(): LocalDate =
    LocalDate.of(year, monthNumber, dayOfMonth)
