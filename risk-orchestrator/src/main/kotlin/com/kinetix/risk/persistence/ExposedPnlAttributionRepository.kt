package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedPnlAttributionRepository(private val db: Database? = null) : PnlAttributionRepository {

    override suspend fun save(attribution: PnlAttribution): Unit = newSuspendedTransaction(db = db) {
        PnlAttributionsTable.upsert(
            PnlAttributionsTable.portfolioId,
            PnlAttributionsTable.attributionDate,
        ) {
            it[portfolioId] = attribution.portfolioId.value
            it[attributionDate] = attribution.date.toKotlinxDate()
            it[totalPnl] = attribution.totalPnl
            it[deltaPnl] = attribution.deltaPnl
            it[gammaPnl] = attribution.gammaPnl
            it[vegaPnl] = attribution.vegaPnl
            it[thetaPnl] = attribution.thetaPnl
            it[rhoPnl] = attribution.rhoPnl
            it[unexplainedPnl] = attribution.unexplainedPnl
            it[positionAttributions] = attribution.positionAttributions.map { pa -> pa.toJson() }
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByPortfolioIdAndDate(
        portfolioId: PortfolioId,
        date: LocalDate,
    ): PnlAttribution? = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where {
                (PnlAttributionsTable.portfolioId eq portfolioId.value) and
                    (PnlAttributionsTable.attributionDate eq date.toKotlinxDate())
            }
            .firstOrNull()
            ?.toPnlAttribution()
    }

    override suspend fun findLatestByPortfolioId(
        portfolioId: PortfolioId,
    ): PnlAttribution? = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where { PnlAttributionsTable.portfolioId eq portfolioId.value }
            .orderBy(PnlAttributionsTable.attributionDate, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toPnlAttribution()
    }

    override suspend fun findByPortfolioId(
        portfolioId: PortfolioId,
    ): List<PnlAttribution> = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where { PnlAttributionsTable.portfolioId eq portfolioId.value }
            .orderBy(PnlAttributionsTable.attributionDate, SortOrder.DESC)
            .map { it.toPnlAttribution() }
    }

    private fun PositionPnlAttribution.toJson(): PositionPnlAttributionJson = PositionPnlAttributionJson(
        instrumentId = instrumentId.value,
        assetClass = assetClass.name,
        totalPnl = totalPnl.toPlainString(),
        deltaPnl = deltaPnl.toPlainString(),
        gammaPnl = gammaPnl.toPlainString(),
        vegaPnl = vegaPnl.toPlainString(),
        thetaPnl = thetaPnl.toPlainString(),
        rhoPnl = rhoPnl.toPlainString(),
        unexplainedPnl = unexplainedPnl.toPlainString(),
    )

    private fun PositionPnlAttributionJson.toDomain(): PositionPnlAttribution = PositionPnlAttribution(
        instrumentId = InstrumentId(instrumentId),
        assetClass = AssetClass.valueOf(assetClass),
        totalPnl = BigDecimal(totalPnl),
        deltaPnl = BigDecimal(deltaPnl),
        gammaPnl = BigDecimal(gammaPnl),
        vegaPnl = BigDecimal(vegaPnl),
        thetaPnl = BigDecimal(thetaPnl),
        rhoPnl = BigDecimal(rhoPnl),
        unexplainedPnl = BigDecimal(unexplainedPnl),
    )

    private fun ResultRow.toPnlAttribution(): PnlAttribution = PnlAttribution(
        portfolioId = PortfolioId(this[PnlAttributionsTable.portfolioId]),
        date = this[PnlAttributionsTable.attributionDate].toJavaDate(),
        totalPnl = this[PnlAttributionsTable.totalPnl],
        deltaPnl = this[PnlAttributionsTable.deltaPnl],
        gammaPnl = this[PnlAttributionsTable.gammaPnl],
        vegaPnl = this[PnlAttributionsTable.vegaPnl],
        thetaPnl = this[PnlAttributionsTable.thetaPnl],
        rhoPnl = this[PnlAttributionsTable.rhoPnl],
        unexplainedPnl = this[PnlAttributionsTable.unexplainedPnl],
        positionAttributions = this[PnlAttributionsTable.positionAttributions]?.map { it.toDomain() } ?: emptyList(),
        calculatedAt = this[PnlAttributionsTable.createdAt].toInstant(),
    )
}
