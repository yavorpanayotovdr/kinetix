package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object IntradayPnlSnapshotsTable : Table("intraday_pnl_snapshots") {
    val id = long("id").autoIncrement()
    val bookId = varchar("book_id", 64)
    val snapshotAt = timestampWithTimeZone("snapshot_at")
    val baseCurrency = varchar("base_currency", 3)
    val trigger = varchar("trigger", 32)
    val totalPnl = decimal("total_pnl", 20, 8)
    val realisedPnl = decimal("realised_pnl", 20, 8)
    val unrealisedPnl = decimal("unrealised_pnl", 20, 8)
    val deltaPnl = decimal("delta_pnl", 20, 8)
    val gammaPnl = decimal("gamma_pnl", 20, 8)
    val vegaPnl = decimal("vega_pnl", 20, 8)
    val thetaPnl = decimal("theta_pnl", 20, 8)
    val rhoPnl = decimal("rho_pnl", 20, 8)
    val unexplainedPnl = decimal("unexplained_pnl", 20, 8)
    val highWaterMark = decimal("high_water_mark", 20, 8)
    val instrumentPnlJson = text("instrument_pnl_json").default("[]")
    val correlationId = varchar("correlation_id", 128).nullable()

    override val primaryKey = PrimaryKey(id, snapshotAt)
}
