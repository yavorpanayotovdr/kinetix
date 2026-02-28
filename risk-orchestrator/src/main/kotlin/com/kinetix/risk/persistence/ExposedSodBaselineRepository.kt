package com.kinetix.risk.persistence

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaseline
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedSodBaselineRepository(private val db: Database? = null) : SodBaselineRepository {

    override suspend fun save(baseline: SodBaseline): Unit = newSuspendedTransaction(db = db) {
        SodBaselinesTable.upsert(
            SodBaselinesTable.portfolioId,
            SodBaselinesTable.baselineDate,
        ) {
            it[portfolioId] = baseline.portfolioId.value
            it[baselineDate] = baseline.baselineDate.toKotlinxDate()
            it[snapshotType] = baseline.snapshotType.name
            it[createdAt] = OffsetDateTime.ofInstant(baseline.createdAt, ZoneOffset.UTC)
        }
    }

    override suspend fun findByPortfolioIdAndDate(
        portfolioId: PortfolioId,
        date: LocalDate,
    ): SodBaseline? = newSuspendedTransaction(db = db) {
        SodBaselinesTable
            .selectAll()
            .where {
                (SodBaselinesTable.portfolioId eq portfolioId.value) and
                    (SodBaselinesTable.baselineDate eq date.toKotlinxDate())
            }
            .singleOrNull()
            ?.toSodBaseline()
    }

    override suspend fun deleteByPortfolioIdAndDate(
        portfolioId: PortfolioId,
        date: LocalDate,
    ): Unit = newSuspendedTransaction(db = db) {
        SodBaselinesTable.deleteWhere {
            (SodBaselinesTable.portfolioId eq portfolioId.value) and
                (SodBaselinesTable.baselineDate eq date.toKotlinxDate())
        }
    }

    private fun ResultRow.toSodBaseline(): SodBaseline = SodBaseline(
        id = this[SodBaselinesTable.id],
        portfolioId = PortfolioId(this[SodBaselinesTable.portfolioId]),
        baselineDate = this[SodBaselinesTable.baselineDate].toJavaDate(),
        snapshotType = SnapshotType.valueOf(this[SodBaselinesTable.snapshotType]),
        createdAt = this[SodBaselinesTable.createdAt].toInstant(),
    )
}
