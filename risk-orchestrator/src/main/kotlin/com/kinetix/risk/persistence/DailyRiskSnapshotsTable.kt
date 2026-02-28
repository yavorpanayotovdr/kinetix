package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object DailyRiskSnapshotsTable : Table("daily_risk_snapshots") {
    val id = long("id").autoIncrement()
    val portfolioId = varchar("portfolio_id", 64)
    val snapshotDate = date("snapshot_date")
    val instrumentId = varchar("instrument_id", 64)
    val assetClass = varchar("asset_class", 32)
    val quantity = decimal("quantity", 20, 8)
    val marketPrice = decimal("market_price", 20, 8)
    val delta = double("delta").nullable()
    val gamma = double("gamma").nullable()
    val vega = double("vega").nullable()
    val theta = double("theta").nullable()
    val rho = double("rho").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
