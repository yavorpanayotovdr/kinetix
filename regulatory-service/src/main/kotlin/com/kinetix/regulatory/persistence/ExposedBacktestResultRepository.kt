package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.BacktestResultRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ExposedBacktestResultRepository(private val db: Database? = null) : BacktestResultRepository {

    override suspend fun save(record: BacktestResultRecord): Unit = newSuspendedTransaction(db = db) {
        BacktestResultsTable.insert {
            it[id] = record.id
            it[bookId] = record.bookId
            it[calculationType] = record.calculationType
            it[confidenceLevel] = record.confidenceLevel
            it[totalDays] = record.totalDays
            it[violationCount] = record.violationCount
            it[violationRate] = record.violationRate
            it[kupiecStatistic] = record.kupiecStatistic
            it[kupiecPValue] = record.kupiecPValue
            it[kupiecPass] = record.kupiecPass
            it[christoffersenStatistic] = record.christoffersenStatistic
            it[christoffersenPValue] = record.christoffersenPValue
            it[christoffersenPass] = record.christoffersenPass
            it[trafficLightZone] = record.trafficLightZone
            it[calculatedAt] = OffsetDateTime.ofInstant(record.calculatedAt, ZoneOffset.UTC)
            it[inputDigest] = record.inputDigest
            it[windowStart] = record.windowStart?.toKotlinxDate()
            it[windowEnd] = record.windowEnd?.toKotlinxDate()
            it[modelVersion] = record.modelVersion
        }
    }

    override suspend fun findByBookId(
        bookId: String,
        limit: Int,
        offset: Int,
        from: Instant?,
    ): List<BacktestResultRecord> = newSuspendedTransaction(db = db) {
        val cutoff = OffsetDateTime.ofInstant(
            from ?: Instant.now().minus(90, ChronoUnit.DAYS),
            ZoneOffset.UTC,
        )
        BacktestResultsTable
            .selectAll()
            .where {
                (BacktestResultsTable.bookId eq bookId) and
                    (BacktestResultsTable.calculatedAt greaterEq cutoff)
            }
            .orderBy(BacktestResultsTable.calculatedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun findById(id: String): BacktestResultRecord? = newSuspendedTransaction(db = db) {
        BacktestResultsTable
            .selectAll()
            .where { BacktestResultsTable.id eq id }
            .firstOrNull()
            ?.toRecord()
    }

    override suspend fun findLatestByBookId(bookId: String): BacktestResultRecord? =
        newSuspendedTransaction(db = db) {
            BacktestResultsTable
                .selectAll()
                .where { BacktestResultsTable.bookId eq bookId }
                .orderBy(BacktestResultsTable.calculatedAt, SortOrder.DESC)
                .limit(1)
                .map { it.toRecord() }
                .firstOrNull()
        }

    private fun ResultRow.toRecord() = BacktestResultRecord(
        id = this[BacktestResultsTable.id],
        bookId = this[BacktestResultsTable.bookId],
        calculationType = this[BacktestResultsTable.calculationType],
        confidenceLevel = this[BacktestResultsTable.confidenceLevel],
        totalDays = this[BacktestResultsTable.totalDays],
        violationCount = this[BacktestResultsTable.violationCount],
        violationRate = this[BacktestResultsTable.violationRate],
        kupiecStatistic = this[BacktestResultsTable.kupiecStatistic],
        kupiecPValue = this[BacktestResultsTable.kupiecPValue],
        kupiecPass = this[BacktestResultsTable.kupiecPass],
        christoffersenStatistic = this[BacktestResultsTable.christoffersenStatistic],
        christoffersenPValue = this[BacktestResultsTable.christoffersenPValue],
        christoffersenPass = this[BacktestResultsTable.christoffersenPass],
        trafficLightZone = this[BacktestResultsTable.trafficLightZone],
        calculatedAt = this[BacktestResultsTable.calculatedAt].toInstant(),
        inputDigest = this[BacktestResultsTable.inputDigest],
        windowStart = this[BacktestResultsTable.windowStart]?.toJavaDate(),
        windowEnd = this[BacktestResultsTable.windowEnd]?.toJavaDate(),
        modelVersion = this[BacktestResultsTable.modelVersion],
    )
}
