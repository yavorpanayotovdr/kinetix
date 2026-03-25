package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.historical.HistoricalScenarioPeriod
import com.kinetix.regulatory.historical.HistoricalScenarioRepository
import com.kinetix.regulatory.historical.HistoricalScenarioReturn
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedHistoricalScenarioRepository(private val db: Database? = null) : HistoricalScenarioRepository {

    override suspend fun savePeriod(period: HistoricalScenarioPeriod): Unit = newSuspendedTransaction(db = db) {
        val existing = HistoricalScenarioPeriodsTable
            .selectAll()
            .where { HistoricalScenarioPeriodsTable.periodId eq period.periodId }
            .firstOrNull()

        if (existing == null) {
            HistoricalScenarioPeriodsTable.insert {
                it[periodId] = period.periodId
                it[name] = period.name
                it[description] = period.description
                it[startDate] = period.startDate
                it[endDate] = period.endDate
                it[assetClassFocus] = period.assetClassFocus
                it[severityLabel] = period.severityLabel
            }
        }
    }

    override suspend fun saveReturns(returns: List<HistoricalScenarioReturn>): Unit =
        newSuspendedTransaction(db = db) {
            for (ret in returns) {
                val existing = HistoricalScenarioReturnsTable
                    .selectAll()
                    .where {
                        (HistoricalScenarioReturnsTable.periodId eq ret.periodId) and
                            (HistoricalScenarioReturnsTable.instrumentId eq ret.instrumentId) and
                            (HistoricalScenarioReturnsTable.returnDate eq ret.returnDate)
                    }
                    .firstOrNull()

                if (existing == null) {
                    HistoricalScenarioReturnsTable.insert {
                        it[periodId] = ret.periodId
                        it[instrumentId] = ret.instrumentId
                        it[returnDate] = ret.returnDate
                        it[dailyReturn] = ret.dailyReturn
                        it[returnSource] = ret.source
                    }
                }
            }
        }

    override suspend fun findAllPeriods(): List<HistoricalScenarioPeriod> = newSuspendedTransaction(db = db) {
        HistoricalScenarioPeriodsTable
            .selectAll()
            .orderBy(HistoricalScenarioPeriodsTable.startDate)
            .map { it.toPeriod() }
    }

    override suspend fun findPeriodById(periodId: String): HistoricalScenarioPeriod? =
        newSuspendedTransaction(db = db) {
            HistoricalScenarioPeriodsTable
                .selectAll()
                .where { HistoricalScenarioPeriodsTable.periodId eq periodId }
                .map { it.toPeriod() }
                .firstOrNull()
        }

    override suspend fun findReturns(periodId: String, instrumentIds: List<String>): List<HistoricalScenarioReturn> =
        newSuspendedTransaction(db = db) {
            HistoricalScenarioReturnsTable
                .selectAll()
                .where {
                    (HistoricalScenarioReturnsTable.periodId eq periodId) and
                        (HistoricalScenarioReturnsTable.instrumentId inList instrumentIds)
                }
                .orderBy(HistoricalScenarioReturnsTable.returnDate)
                .map { it.toReturn() }
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toPeriod() = HistoricalScenarioPeriod(
        periodId = this[HistoricalScenarioPeriodsTable.periodId],
        name = this[HistoricalScenarioPeriodsTable.name],
        description = this[HistoricalScenarioPeriodsTable.description],
        startDate = this[HistoricalScenarioPeriodsTable.startDate],
        endDate = this[HistoricalScenarioPeriodsTable.endDate],
        assetClassFocus = this[HistoricalScenarioPeriodsTable.assetClassFocus],
        severityLabel = this[HistoricalScenarioPeriodsTable.severityLabel],
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toReturn() = HistoricalScenarioReturn(
        periodId = this[HistoricalScenarioReturnsTable.periodId],
        instrumentId = this[HistoricalScenarioReturnsTable.instrumentId],
        returnDate = this[HistoricalScenarioReturnsTable.returnDate],
        dailyReturn = this[HistoricalScenarioReturnsTable.dailyReturn],
        source = this[HistoricalScenarioReturnsTable.returnSource],
    )
}
