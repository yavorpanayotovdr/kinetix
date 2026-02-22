package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedFrtbCalculationRepository(private val db: Database? = null) : FrtbCalculationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(record: FrtbCalculationRecord): Unit = newSuspendedTransaction(db = db) {
        FrtbCalculationsTable.insert {
            it[id] = record.id
            it[portfolioId] = record.portfolioId
            it[totalSbmCharge] = record.totalSbmCharge
            it[grossJtd] = record.grossJtd
            it[hedgeBenefit] = record.hedgeBenefit
            it[netDrc] = record.netDrc
            it[exoticNotional] = record.exoticNotional
            it[otherNotional] = record.otherNotional
            it[totalRrao] = record.totalRrao
            it[totalCapitalCharge] = record.totalCapitalCharge
            it[sbmChargesJson] = json.encodeToString(record.sbmCharges.map { ch ->
                SbmChargeJson(ch.riskClass, ch.deltaCharge, ch.vegaCharge, ch.curvatureCharge, ch.totalCharge)
            })
            it[calculatedAt] = OffsetDateTime.ofInstant(record.calculatedAt, ZoneOffset.UTC)
            it[storedAt] = OffsetDateTime.ofInstant(record.storedAt, ZoneOffset.UTC)
        }
    }

    override suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int,
        offset: Int,
    ): List<FrtbCalculationRecord> = newSuspendedTransaction(db = db) {
        FrtbCalculationsTable
            .selectAll()
            .where { FrtbCalculationsTable.portfolioId eq portfolioId }
            .orderBy(FrtbCalculationsTable.calculatedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun findLatestByPortfolioId(portfolioId: String): FrtbCalculationRecord? =
        newSuspendedTransaction(db = db) {
            FrtbCalculationsTable
                .selectAll()
                .where { FrtbCalculationsTable.portfolioId eq portfolioId }
                .orderBy(FrtbCalculationsTable.calculatedAt, SortOrder.DESC)
                .limit(1)
                .map { it.toRecord() }
                .firstOrNull()
        }

    private fun ResultRow.toRecord(): FrtbCalculationRecord {
        val charges = json.decodeFromString<List<SbmChargeJson>>(this[FrtbCalculationsTable.sbmChargesJson])
        return FrtbCalculationRecord(
            id = this[FrtbCalculationsTable.id],
            portfolioId = this[FrtbCalculationsTable.portfolioId],
            totalSbmCharge = this[FrtbCalculationsTable.totalSbmCharge],
            grossJtd = this[FrtbCalculationsTable.grossJtd],
            hedgeBenefit = this[FrtbCalculationsTable.hedgeBenefit],
            netDrc = this[FrtbCalculationsTable.netDrc],
            exoticNotional = this[FrtbCalculationsTable.exoticNotional],
            otherNotional = this[FrtbCalculationsTable.otherNotional],
            totalRrao = this[FrtbCalculationsTable.totalRrao],
            totalCapitalCharge = this[FrtbCalculationsTable.totalCapitalCharge],
            sbmCharges = charges.map {
                RiskClassCharge(it.riskClass, it.deltaCharge, it.vegaCharge, it.curvatureCharge, it.totalCharge)
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
