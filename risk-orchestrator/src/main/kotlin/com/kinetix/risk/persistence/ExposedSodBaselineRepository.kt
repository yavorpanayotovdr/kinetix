package com.kinetix.risk.persistence

import com.kinetix.common.model.BookId
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
            SodBaselinesTable.bookId,
            SodBaselinesTable.baselineDate,
        ) {
            it[bookId] = baseline.bookId.value
            it[baselineDate] = baseline.baselineDate.toKotlinxDate()
            it[snapshotType] = baseline.snapshotType.name
            it[createdAt] = OffsetDateTime.ofInstant(baseline.createdAt, ZoneOffset.UTC)
            it[sourceJobId] = baseline.sourceJobId
            it[calculationType] = baseline.calculationType
            it[varValue] = baseline.varValue
            it[expectedShortfall] = baseline.expectedShortfall
            it[greekSnapshotId] = baseline.greekSnapshotId
        }
    }

    override suspend fun findByBookIdAndDate(
        bookId: BookId,
        date: LocalDate,
    ): SodBaseline? = newSuspendedTransaction(db = db) {
        SodBaselinesTable
            .selectAll()
            .where {
                (SodBaselinesTable.bookId eq bookId.value) and
                    (SodBaselinesTable.baselineDate eq date.toKotlinxDate())
            }
            .singleOrNull()
            ?.toSodBaseline()
    }

    override suspend fun deleteByBookIdAndDate(
        bookId: BookId,
        date: LocalDate,
    ): Unit = newSuspendedTransaction(db = db) {
        SodBaselinesTable.deleteWhere {
            (SodBaselinesTable.bookId eq bookId.value) and
                (SodBaselinesTable.baselineDate eq date.toKotlinxDate())
        }
    }

    private fun ResultRow.toSodBaseline(): SodBaseline = SodBaseline(
        id = this[SodBaselinesTable.id],
        bookId = BookId(this[SodBaselinesTable.bookId]),
        baselineDate = this[SodBaselinesTable.baselineDate].toJavaDate(),
        snapshotType = SnapshotType.valueOf(this[SodBaselinesTable.snapshotType]),
        createdAt = this[SodBaselinesTable.createdAt].toInstant(),
        sourceJobId = this[SodBaselinesTable.sourceJobId],
        calculationType = this[SodBaselinesTable.calculationType],
        varValue = this[SodBaselinesTable.varValue],
        expectedShortfall = this[SodBaselinesTable.expectedShortfall],
        greekSnapshotId = this[SodBaselinesTable.greekSnapshotId],
    )
}
