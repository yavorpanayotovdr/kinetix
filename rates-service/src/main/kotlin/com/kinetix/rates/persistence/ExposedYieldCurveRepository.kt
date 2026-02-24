package com.kinetix.rates.persistence

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.Tenor
import com.kinetix.common.model.YieldCurve
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedYieldCurveRepository(private val db: Database? = null) : YieldCurveRepository {

    override suspend fun save(curve: YieldCurve): Unit = newSuspendedTransaction(db = db) {
        val asOfOffset = curve.asOf.atOffset(ZoneOffset.UTC)
        YieldCurveTable.insert {
            it[curveId] = curve.curveId
            it[asOfDate] = asOfOffset
            it[currency] = curve.currency.currencyCode
            it[dataSource] = curve.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        for (tenor in curve.tenors) {
            YieldCurveTenorTable.insert {
                it[curveId] = curve.curveId
                it[asOfDate] = asOfOffset
                it[label] = tenor.label
                it[days] = tenor.days
                it[rate] = tenor.rate
            }
        }
    }

    override suspend fun findLatest(curveId: String): YieldCurve? = newSuspendedTransaction(db = db) {
        val latestHeader = YieldCurveTable
            .selectAll()
            .where { YieldCurveTable.curveId eq curveId }
            .orderBy(YieldCurveTable.asOfDate, SortOrder.DESC)
            .limit(1)
            .singleOrNull() ?: return@newSuspendedTransaction null

        val asOfDate = latestHeader[YieldCurveTable.asOfDate]
        val tenorRows = YieldCurveTenorTable
            .selectAll()
            .where {
                (YieldCurveTenorTable.curveId eq curveId) and
                    (YieldCurveTenorTable.asOfDate eq asOfDate)
            }
            .orderBy(YieldCurveTenorTable.days, SortOrder.ASC)
            .toList()

        toYieldCurve(latestHeader, tenorRows)
    }

    override suspend fun findByTimeRange(
        curveId: String,
        from: Instant,
        to: Instant,
    ): List<YieldCurve> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)

        val headers = YieldCurveTable
            .selectAll()
            .where {
                (YieldCurveTable.curveId eq curveId) and
                    (YieldCurveTable.asOfDate greaterEq fromOffset) and
                    (YieldCurveTable.asOfDate lessEq toOffset)
            }
            .orderBy(YieldCurveTable.asOfDate, SortOrder.ASC)
            .toList()

        headers.map { header ->
            val tenorRows = YieldCurveTenorTable
                .selectAll()
                .where {
                    (YieldCurveTenorTable.curveId eq header[YieldCurveTable.curveId]) and
                        (YieldCurveTenorTable.asOfDate eq header[YieldCurveTable.asOfDate])
                }
                .orderBy(YieldCurveTenorTable.days, SortOrder.ASC)
                .toList()
            toYieldCurve(header, tenorRows)
        }
    }

    private fun toYieldCurve(header: ResultRow, tenorRows: List<ResultRow>): YieldCurve {
        val tenors = tenorRows.map { row ->
            Tenor(
                label = row[YieldCurveTenorTable.label],
                days = row[YieldCurveTenorTable.days],
                rate = row[YieldCurveTenorTable.rate],
            )
        }
        return YieldCurve(
            currency = Currency.getInstance(header[YieldCurveTable.currency]),
            asOf = header[YieldCurveTable.asOfDate].toInstant(),
            tenors = tenors,
            curveId = header[YieldCurveTable.curveId],
            source = RateSource.valueOf(header[YieldCurveTable.dataSource]),
        )
    }
}
