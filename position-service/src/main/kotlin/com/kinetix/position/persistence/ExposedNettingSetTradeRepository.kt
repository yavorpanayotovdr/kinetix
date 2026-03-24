package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedNettingSetTradeRepository(
    private val db: Database? = null,
) : NettingSetTradeRepository {

    override suspend fun findNettingSetsByTradeIds(tradeIds: List<String>): Map<String, String> {
        if (tradeIds.isEmpty()) return emptyMap()
        return newSuspendedTransaction(db = db) {
            NettingSetTradesTable
                .selectAll()
                .where { NettingSetTradesTable.tradeId inList tradeIds }
                .associate {
                    it[NettingSetTradesTable.tradeId] to it[NettingSetTradesTable.nettingSetId]
                }
        }
    }

    override suspend fun assign(tradeId: String, nettingSetId: String) {
        newSuspendedTransaction(db = db) {
            // Upsert: ignore if already assigned (primary key conflict)
            val exists = NettingSetTradesTable
                .selectAll()
                .where { NettingSetTradesTable.tradeId eq tradeId }
                .count() > 0

            if (!exists) {
                NettingSetTradesTable.insert {
                    it[NettingSetTradesTable.tradeId] = tradeId
                    it[NettingSetTradesTable.nettingSetId] = nettingSetId
                    it[NettingSetTradesTable.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
    }
}
