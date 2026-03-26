package com.kinetix.risk.persistence

import com.kinetix.risk.client.SaCcrResult
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

interface SaCcrResultRepository {
    suspend fun save(result: SaCcrResult, positionCount: Int, collateralNet: Double)
    suspend fun findLatestByCounterparty(counterpartyId: String): SaCcrResult?
}

class ExposedSaCcrResultRepository(private val db: Database? = null) : SaCcrResultRepository {

    override suspend fun save(result: SaCcrResult, positionCount: Int, collateralNet: Double) {
        newSuspendedTransaction(db = db) {
            SaCcrResultsTable.insert {
                it[counterpartyId] = result.counterpartyId
                it[nettingSetId] = result.nettingSetId
                it[calculatedAt] = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                it[replacementCost] = BigDecimal.valueOf(result.replacementCost)
                it[pfeAddon] = BigDecimal.valueOf(result.pfeAddon)
                it[multiplier] = BigDecimal.valueOf(result.multiplier)
                it[ead] = BigDecimal.valueOf(result.ead)
                it[alpha] = BigDecimal.valueOf(result.alpha)
                it[SaCcrResultsTable.collateralNet] = BigDecimal.valueOf(collateralNet)
                it[SaCcrResultsTable.positionCount] = positionCount
            }
        }
    }

    override suspend fun findLatestByCounterparty(counterpartyId: String): SaCcrResult? {
        return newSuspendedTransaction(db = db) {
            SaCcrResultsTable.selectAll()
                .where { SaCcrResultsTable.counterpartyId eq counterpartyId }
                .orderBy(SaCcrResultsTable.calculatedAt, SortOrder.DESC)
                .limit(1)
                .map {
                    SaCcrResult(
                        nettingSetId = it[SaCcrResultsTable.nettingSetId],
                        counterpartyId = it[SaCcrResultsTable.counterpartyId],
                        replacementCost = it[SaCcrResultsTable.replacementCost].toDouble(),
                        pfeAddon = it[SaCcrResultsTable.pfeAddon].toDouble(),
                        multiplier = it[SaCcrResultsTable.multiplier].toDouble(),
                        ead = it[SaCcrResultsTable.ead].toDouble(),
                        alpha = it[SaCcrResultsTable.alpha].toDouble(),
                    )
                }
                .firstOrNull()
        }
    }
}
