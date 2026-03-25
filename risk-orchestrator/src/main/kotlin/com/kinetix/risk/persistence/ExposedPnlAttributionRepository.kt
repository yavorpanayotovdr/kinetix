package com.kinetix.risk.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.AttributionDataQuality
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
            PnlAttributionsTable.bookId,
            PnlAttributionsTable.attributionDate,
        ) {
            it[bookId] = attribution.bookId.value
            it[attributionDate] = attribution.date.toKotlinxDate()
            it[currency] = attribution.currency
            it[totalPnl] = attribution.totalPnl
            it[deltaPnl] = attribution.deltaPnl
            it[gammaPnl] = attribution.gammaPnl
            it[vegaPnl] = attribution.vegaPnl
            it[thetaPnl] = attribution.thetaPnl
            it[rhoPnl] = attribution.rhoPnl
            it[vannaPnl] = attribution.vannaPnl
            it[volgaPnl] = attribution.volgaPnl
            it[charmPnl] = attribution.charmPnl
            it[crossGammaPnl] = attribution.crossGammaPnl
            it[unexplainedPnl] = attribution.unexplainedPnl
            it[positionAttributions] = attribution.positionAttributions.map { pa -> pa.toJson() }
            it[dataQualityFlag] = attribution.dataQualityFlag.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByBookIdAndDate(
        bookId: BookId,
        date: LocalDate,
    ): PnlAttribution? = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where {
                (PnlAttributionsTable.bookId eq bookId.value) and
                    (PnlAttributionsTable.attributionDate eq date.toKotlinxDate())
            }
            .firstOrNull()
            ?.toPnlAttribution()
    }

    override suspend fun findLatestByBookId(
        bookId: BookId,
    ): PnlAttribution? = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where { PnlAttributionsTable.bookId eq bookId.value }
            .orderBy(PnlAttributionsTable.attributionDate, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toPnlAttribution()
    }

    override suspend fun findByBookId(
        bookId: BookId,
        fromDate: LocalDate,
    ): List<PnlAttribution> = newSuspendedTransaction(db = db) {
        PnlAttributionsTable
            .selectAll()
            .where {
                (PnlAttributionsTable.bookId eq bookId.value) and
                    (PnlAttributionsTable.attributionDate greaterEq fromDate.toKotlinxDate())
            }
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
        vannaPnl = vannaPnl.toPlainString(),
        volgaPnl = volgaPnl.toPlainString(),
        charmPnl = charmPnl.toPlainString(),
        crossGammaPnl = crossGammaPnl.toPlainString(),
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
        vannaPnl = BigDecimal(vannaPnl),
        volgaPnl = BigDecimal(volgaPnl),
        charmPnl = BigDecimal(charmPnl),
        crossGammaPnl = BigDecimal(crossGammaPnl),
        unexplainedPnl = BigDecimal(unexplainedPnl),
    )

    private fun ResultRow.toPnlAttribution(): PnlAttribution = PnlAttribution(
        bookId = BookId(this[PnlAttributionsTable.bookId]),
        date = this[PnlAttributionsTable.attributionDate].toJavaDate(),
        currency = this[PnlAttributionsTable.currency],
        totalPnl = this[PnlAttributionsTable.totalPnl],
        deltaPnl = this[PnlAttributionsTable.deltaPnl],
        gammaPnl = this[PnlAttributionsTable.gammaPnl],
        vegaPnl = this[PnlAttributionsTable.vegaPnl],
        thetaPnl = this[PnlAttributionsTable.thetaPnl],
        rhoPnl = this[PnlAttributionsTable.rhoPnl],
        vannaPnl = this[PnlAttributionsTable.vannaPnl],
        volgaPnl = this[PnlAttributionsTable.volgaPnl],
        charmPnl = this[PnlAttributionsTable.charmPnl],
        crossGammaPnl = this[PnlAttributionsTable.crossGammaPnl],
        unexplainedPnl = this[PnlAttributionsTable.unexplainedPnl],
        positionAttributions = this[PnlAttributionsTable.positionAttributions]?.map { it.toDomain() } ?: emptyList(),
        dataQualityFlag = runCatching {
            AttributionDataQuality.valueOf(this[PnlAttributionsTable.dataQualityFlag])
        }.getOrDefault(AttributionDataQuality.PRICE_ONLY),
        calculatedAt = this[PnlAttributionsTable.createdAt].toInstant(),
    )
}
