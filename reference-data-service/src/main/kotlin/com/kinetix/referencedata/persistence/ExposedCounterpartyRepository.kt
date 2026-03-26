package com.kinetix.referencedata.persistence

import com.kinetix.referencedata.model.Counterparty
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedCounterpartyRepository(
    private val db: Database? = null,
) : CounterpartyRepository {

    override suspend fun findById(counterpartyId: String): Counterparty? =
        newSuspendedTransaction(db = db) {
            CounterpartyMasterTable
                .selectAll()
                .where { CounterpartyMasterTable.counterpartyId eq counterpartyId }
                .singleOrNull()
                ?.toCounterparty()
        }

    override suspend fun findAll(): List<Counterparty> =
        newSuspendedTransaction(db = db) {
            CounterpartyMasterTable
                .selectAll()
                .orderBy(CounterpartyMasterTable.legalName)
                .map { it.toCounterparty() }
        }

    override suspend fun upsert(counterparty: Counterparty): Unit =
        newSuspendedTransaction(db = db) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            CounterpartyMasterTable.upsert {
                it[counterpartyId] = counterparty.counterpartyId
                it[legalName] = counterparty.legalName
                it[shortName] = counterparty.shortName
                it[lei] = counterparty.lei
                it[ratingSp] = counterparty.ratingSp
                it[ratingMoodys] = counterparty.ratingMoodys
                it[ratingFitch] = counterparty.ratingFitch
                it[sector] = counterparty.sector
                it[country] = counterparty.country
                it[isFinancial] = counterparty.isFinancial
                it[pd1y] = counterparty.pd1y
                it[lgd] = counterparty.lgd
                it[cdsSpreadBps] = counterparty.cdsSpreadBps
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

    private fun ResultRow.toCounterparty() = Counterparty(
        counterpartyId = this[CounterpartyMasterTable.counterpartyId],
        legalName = this[CounterpartyMasterTable.legalName],
        shortName = this[CounterpartyMasterTable.shortName],
        lei = this[CounterpartyMasterTable.lei],
        ratingSp = this[CounterpartyMasterTable.ratingSp],
        ratingMoodys = this[CounterpartyMasterTable.ratingMoodys],
        ratingFitch = this[CounterpartyMasterTable.ratingFitch],
        sector = this[CounterpartyMasterTable.sector],
        country = this[CounterpartyMasterTable.country],
        isFinancial = this[CounterpartyMasterTable.isFinancial],
        pd1y = this[CounterpartyMasterTable.pd1y],
        lgd = this[CounterpartyMasterTable.lgd],
        cdsSpreadBps = this[CounterpartyMasterTable.cdsSpreadBps],
        createdAt = this[CounterpartyMasterTable.createdAt].toInstant(),
        updatedAt = this[CounterpartyMasterTable.updatedAt].toInstant(),
    )
}
