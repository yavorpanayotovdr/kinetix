package com.kinetix.audit.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object AuditEventsTable : Table("audit_events") {
    val id = long("id").autoIncrement()
    val tradeId = varchar("trade_id", 255)
    val portfolioId = varchar("portfolio_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val assetClass = varchar("asset_class", 50)
    val side = varchar("side", 10)
    val quantity = varchar("quantity", 50)
    val priceAmount = varchar("price_amount", 50)
    val priceCurrency = varchar("price_currency", 3)
    val tradedAt = varchar("traded_at", 50)
    val receivedAt = timestampWithTimeZone("received_at")

    override val primaryKey = PrimaryKey(id)
}
