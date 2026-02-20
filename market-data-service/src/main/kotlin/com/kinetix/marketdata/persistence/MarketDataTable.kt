package com.kinetix.marketdata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object MarketDataTable : Table("market_data") {
    val instrumentId = varchar("instrument_id", 255)
    val priceAmount = decimal("price_amount", 28, 12)
    val priceCurrency = varchar("price_currency", 3)
    val timestamp = timestampWithTimeZone("timestamp")
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(instrumentId, timestamp)
}
