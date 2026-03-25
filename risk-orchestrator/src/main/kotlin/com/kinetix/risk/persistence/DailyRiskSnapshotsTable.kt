package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object DailyRiskSnapshotsTable : Table("daily_risk_snapshots") {
    val id = long("id").autoIncrement()
    val bookId = varchar("book_id", 64)
    val snapshotDate = timestampWithTimeZone("snapshot_date")
    val instrumentId = varchar("instrument_id", 255)
    val assetClass = varchar("asset_class", 32)
    val quantity = decimal("quantity", 20, 8)
    val marketPrice = decimal("market_price", 20, 8)
    val delta = double("delta").nullable()
    val gamma = double("gamma").nullable()
    val vega = double("vega").nullable()
    val theta = double("theta").nullable()
    val rho = double("rho").nullable()
    val varContribution = decimal("var_contribution", 28, 8).nullable()
    val esContribution = decimal("es_contribution", 28, 8).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id, snapshotDate)
}
