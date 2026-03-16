package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object RunMarketDataBlobsTable : Table("run_market_data_blobs") {
    val contentHash = varchar("content_hash", 64)
    val dataType = varchar("data_type", 50)
    val instrumentId = varchar("instrument_id", 255)
    val assetClass = varchar("asset_class", 32)
    val payload = jsonb<JsonElement>("payload", Json)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(contentHash)
}
