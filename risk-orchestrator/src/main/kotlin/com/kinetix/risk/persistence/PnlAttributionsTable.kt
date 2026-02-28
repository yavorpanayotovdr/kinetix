package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object PnlAttributionsTable : Table("pnl_attributions") {
    val id = long("id").autoIncrement()
    val portfolioId = varchar("portfolio_id", 64)
    val attributionDate = date("attribution_date")
    val totalPnl = decimal("total_pnl", 20, 8)
    val deltaPnl = decimal("delta_pnl", 20, 8)
    val gammaPnl = decimal("gamma_pnl", 20, 8)
    val vegaPnl = decimal("vega_pnl", 20, 8)
    val thetaPnl = decimal("theta_pnl", 20, 8)
    val rhoPnl = decimal("rho_pnl", 20, 8)
    val unexplainedPnl = decimal("unexplained_pnl", 20, 8)
    val positionAttributions = jsonb<List<PositionPnlAttributionJson>>("position_attributions", Json).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
