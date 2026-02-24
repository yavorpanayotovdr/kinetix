package com.kinetix.rates.persistence

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.RiskFreeRate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

class ExposedRiskFreeRateRepository(private val db: Database? = null) : RiskFreeRateRepository {

    override suspend fun save(rate: RiskFreeRate): Unit = newSuspendedTransaction(db = db) {
        RiskFreeRateTable.insert {
            it[currency] = rate.currency.currencyCode
            it[tenor] = rate.tenor
            it[asOfDate] = rate.asOfDate.atOffset(ZoneOffset.UTC)
            it[RiskFreeRateTable.rate] = rate.rate.toBigDecimal()
            it[dataSource] = rate.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findLatest(currency: Currency, tenor: String): RiskFreeRate? =
        newSuspendedTransaction(db = db) {
            RiskFreeRateTable
                .selectAll()
                .where {
                    (RiskFreeRateTable.currency eq currency.currencyCode) and
                        (RiskFreeRateTable.tenor eq tenor)
                }
                .orderBy(RiskFreeRateTable.asOfDate, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toRiskFreeRate()
        }

    override suspend fun findByTimeRange(
        currency: Currency,
        tenor: String,
        from: Instant,
        to: Instant,
    ): List<RiskFreeRate> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)
        RiskFreeRateTable
            .selectAll()
            .where {
                (RiskFreeRateTable.currency eq currency.currencyCode) and
                    (RiskFreeRateTable.tenor eq tenor) and
                    (RiskFreeRateTable.asOfDate greaterEq fromOffset) and
                    (RiskFreeRateTable.asOfDate lessEq toOffset)
            }
            .orderBy(RiskFreeRateTable.asOfDate, SortOrder.ASC)
            .map { it.toRiskFreeRate() }
    }

    private fun ResultRow.toRiskFreeRate(): RiskFreeRate = RiskFreeRate(
        currency = Currency.getInstance(this[RiskFreeRateTable.currency]),
        tenor = this[RiskFreeRateTable.tenor],
        rate = this[RiskFreeRateTable.rate].toDouble(),
        asOfDate = this[RiskFreeRateTable.asOfDate].toInstant(),
        source = RateSource.valueOf(this[RiskFreeRateTable.dataSource]),
    )
}
