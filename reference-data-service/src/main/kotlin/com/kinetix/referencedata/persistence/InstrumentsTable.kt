package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object InstrumentsTable : Table("instruments") {
    val instrumentId = varchar("instrument_id", 255)
    val instrumentType = varchar("instrument_type", 50)
    val displayName = varchar("display_name", 255)
    val assetClass = varchar("asset_class", 50)
    val currency = varchar("currency", 3)
    val attributes = text("attributes")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(instrumentId)
}
