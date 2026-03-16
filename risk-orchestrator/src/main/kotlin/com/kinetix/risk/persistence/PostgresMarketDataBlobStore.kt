package com.kinetix.risk.persistence

import com.kinetix.risk.service.MarketDataBlobStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PostgresMarketDataBlobStore(private val db: Database? = null) : MarketDataBlobStore {

    override suspend fun putIfAbsent(
        contentHash: String,
        dataType: String,
        instrumentId: String,
        assetClass: String,
        payload: String,
    ): Unit = newSuspendedTransaction(db = db) {
        val exists = RunMarketDataBlobsTable
            .selectAll()
            .where { RunMarketDataBlobsTable.contentHash eq contentHash }
            .count() > 0

        if (!exists) {
            RunMarketDataBlobsTable.insert {
                it[RunMarketDataBlobsTable.contentHash] = contentHash
                it[RunMarketDataBlobsTable.dataType] = dataType
                it[RunMarketDataBlobsTable.instrumentId] = instrumentId
                it[RunMarketDataBlobsTable.assetClass] = assetClass
                it[RunMarketDataBlobsTable.payload] = Json.parseToJsonElement(payload)
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    override suspend fun get(contentHash: String): String? = newSuspendedTransaction(db = db) {
        RunMarketDataBlobsTable
            .selectAll()
            .where { RunMarketDataBlobsTable.contentHash eq contentHash }
            .firstOrNull()
            ?.get(RunMarketDataBlobsTable.payload)
            ?.let { Json.encodeToString(JsonElement.serializer(), it) }
    }
}
