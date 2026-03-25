package com.kinetix.risk.persistence

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRPoint
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedIntradayVaRTimelineRepository(private val db: Database? = null) : IntradayVaRTimelineRepository {

    override suspend fun findInRange(
        bookId: BookId,
        from: Instant,
        to: Instant,
    ): List<IntradayVaRPoint> = newSuspendedTransaction(db = db) {
        val fromOdt = OffsetDateTime.ofInstant(from, ZoneOffset.UTC)
        val toOdt = OffsetDateTime.ofInstant(to, ZoneOffset.UTC)

        ValuationJobsTable
            .selectAll()
            .where {
                (ValuationJobsTable.bookId eq bookId.value) and
                    (ValuationJobsTable.status eq "COMPLETED") and
                    (ValuationJobsTable.startedAt greaterEq fromOdt) and
                    (ValuationJobsTable.startedAt lessEq toOdt) and
                    (ValuationJobsTable.varValue.isNotNull())
            }
            .orderBy(ValuationJobsTable.startedAt, SortOrder.ASC)
            .map { row ->
                IntradayVaRPoint(
                    timestamp = row[ValuationJobsTable.startedAt].toInstant(),
                    varValue = row[ValuationJobsTable.varValue]!!,
                    expectedShortfall = row[ValuationJobsTable.expectedShortfall] ?: 0.0,
                    delta = row[ValuationJobsTable.delta],
                    gamma = row[ValuationJobsTable.gamma],
                    vega = row[ValuationJobsTable.vega],
                )
            }
    }
}
