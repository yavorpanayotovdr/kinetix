package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.BacktestResultRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedBacktestResultRepository(private val db: Database? = null) : BacktestResultRepository {

    override suspend fun save(record: BacktestResultRecord): Unit = newSuspendedTransaction(db = db) {
        BacktestResultsTable.insert {
            it[id] = record.id
            it[portfolioId] = record.portfolioId
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
        }
    }

    override suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int,
        offset: Int,
    ): List<BacktestResultRecord> = newSuspendedTransaction(db = db) {
        BacktestResultsTable
            .selectAll()
            .where { BacktestResultsTable.portfolioId eq portfolioId }
            .orderBy(BacktestResultsTable.calculatedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun findLatestByPortfolioId(portfolioId: String): BacktestResultRecord? =
        newSuspendedTransaction(db = db) {
            BacktestResultsTable
                .selectAll()
                .where { BacktestResultsTable.portfolioId eq portfolioId }
                .orderBy(BacktestResultsTable.calculatedAt, SortOrder.DESC)
                .limit(1)
                .map { it.toRecord() }
                .firstOrNull()
        }

    private fun ResultRow.toRecord() = BacktestResultRecord(
        id = this[BacktestResultsTable.id],
        portfolioId = this[BacktestResultsTable.portfolioId],
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
    )
}
