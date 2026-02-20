package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object PositionsTable : Table("positions") {
    val portfolioId = varchar("portfolio_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val assetClass = varchar("asset_class", 50)
    val quantity = decimal("quantity", 28, 12)
    val avgCostAmount = decimal("avg_cost_amount", 28, 12)
    val marketPriceAmount = decimal("market_price_amount", 28, 12)
    val currency = varchar("currency", 3)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(portfolioId, instrumentId)
}
