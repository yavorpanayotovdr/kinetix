package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ExposedFrtbCalculationRepository(private val db: Database? = null) : FrtbCalculationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(record: FrtbCalculationRecord): Unit = newSuspendedTransaction(db = db) {
        FrtbCalculationsTable.insert {
            it[id] = record.id
            it[bookId] = record.bookId
            it[totalSbmCharge] = record.totalSbmCharge
            it[grossJtd] = record.grossJtd
            it[hedgeBenefit] = record.hedgeBenefit
            it[netDrc] = record.netDrc
            it[exoticNotional] = record.exoticNotional
            it[otherNotional] = record.otherNotional
            it[totalRrao] = record.totalRrao
            it[totalCapitalCharge] = record.totalCapitalCharge
            it[sbmChargesJson] = json.parseToJsonElement(
                json.encodeToString(record.sbmCharges.map { ch ->
                    SbmChargeJson(
                        riskClass = ch.riskClass,
                        deltaCharge = ch.deltaCharge.toDouble(),
                        vegaCharge = ch.vegaCharge.toDouble(),
                        curvatureCharge = ch.curvatureCharge.toDouble(),
                        totalCharge = ch.totalCharge.toDouble(),
                    )
                })
            )
            it[calculatedAt] = OffsetDateTime.ofInstant(record.calculatedAt, ZoneOffset.UTC)
            it[storedAt] = OffsetDateTime.ofInstant(record.storedAt, ZoneOffset.UTC)
        }
    }

    override suspend fun findByBookId(
        bookId: String,
        limit: Int,
        offset: Int,
        from: Instant?,
    ): List<FrtbCalculationRecord> = newSuspendedTransaction(db = db) {
        val cutoff = OffsetDateTime.ofInstant(
            from ?: Instant.now().minus(90, ChronoUnit.DAYS),
            ZoneOffset.UTC,
        )
        FrtbCalculationsTable
            .selectAll()
            .where {
                (FrtbCalculationsTable.bookId eq bookId) and
                    (FrtbCalculationsTable.calculatedAt greaterEq cutoff)
            }
            .orderBy(FrtbCalculationsTable.calculatedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun findLatestByBookId(bookId: String): FrtbCalculationRecord? =
        newSuspendedTransaction(db = db) {
            FrtbCalculationsTable
                .selectAll()
                .where { FrtbCalculationsTable.bookId eq bookId }
                .orderBy(FrtbCalculationsTable.calculatedAt, SortOrder.DESC)
                .limit(1)
                .map { it.toRecord() }
                .firstOrNull()
        }

    private fun ResultRow.toRecord(): FrtbCalculationRecord {
        val charges = json.decodeFromJsonElement<List<SbmChargeJson>>(this[FrtbCalculationsTable.sbmChargesJson])
        return FrtbCalculationRecord(
            id = this[FrtbCalculationsTable.id],
            bookId = this[FrtbCalculationsTable.bookId],
            totalSbmCharge = this[FrtbCalculationsTable.totalSbmCharge],
            grossJtd = this[FrtbCalculationsTable.grossJtd],
            hedgeBenefit = this[FrtbCalculationsTable.hedgeBenefit],
            netDrc = this[FrtbCalculationsTable.netDrc],
            exoticNotional = this[FrtbCalculationsTable.exoticNotional],
            otherNotional = this[FrtbCalculationsTable.otherNotional],
            totalRrao = this[FrtbCalculationsTable.totalRrao],
            totalCapitalCharge = this[FrtbCalculationsTable.totalCapitalCharge],
            sbmCharges = charges.map {
                RiskClassCharge(
                    riskClass = it.riskClass,
                    deltaCharge = BigDecimal.valueOf(it.deltaCharge),
                    vegaCharge = BigDecimal.valueOf(it.vegaCharge),
                    curvatureCharge = BigDecimal.valueOf(it.curvatureCharge),
                    totalCharge = BigDecimal.valueOf(it.totalCharge),
                )
            },
            calculatedAt = this[FrtbCalculationsTable.calculatedAt].toInstant(),
            storedAt = this[FrtbCalculationsTable.storedAt].toInstant(),
        )
    }

    @kotlinx.serialization.Serializable
    private data class SbmChargeJson(
        val riskClass: String,
        val deltaCharge: Double,
        val vegaCharge: Double,
        val curvatureCharge: Double,
        val totalCharge: Double,
    )
}
