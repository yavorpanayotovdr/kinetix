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
    val quantity = decimal("quantity", 28, 12)
    val priceAmount = decimal("price_amount", 28, 12)
    val priceCurrency = varchar("price_currency", 3)
    val tradedAt = timestampWithTimeZone("traded_at")
    val receivedAt = timestampWithTimeZone("received_at")
    val previousHash = varchar("previous_hash", 64).nullable()
    val recordHash = varchar("record_hash", 64).default("")
    val userId = varchar("user_id", 255).nullable()
    val userRole = varchar("user_role", 100).nullable()
    val eventType = varchar("event_type", 100).default("TRADE_BOOKED")

    override val primaryKey = PrimaryKey(id)
}
