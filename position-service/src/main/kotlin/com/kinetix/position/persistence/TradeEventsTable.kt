package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object TradeEventsTable : Table("trade_events") {
    val tradeId = varchar("trade_id", 255)
    val portfolioId = varchar("portfolio_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val assetClass = varchar("asset_class", 50)
    val side = varchar("side", 10)
    val quantity = decimal("quantity", 28, 12)
    val priceAmount = decimal("price_amount", 28, 12)
    val priceCurrency = varchar("price_currency", 3)
    val tradedAt = timestampWithTimeZone("traded_at")
    val createdAt = timestampWithTimeZone("created_at")
    val tradeType = varchar("trade_type", 10).default("NEW")
    val status = varchar("status", 20).default("LIVE")
    val originalTradeId = varchar("original_trade_id", 255).nullable()
    val counterpartyId = varchar("counterparty_id", 255).nullable()

    override val primaryKey = PrimaryKey(tradeId)
}
