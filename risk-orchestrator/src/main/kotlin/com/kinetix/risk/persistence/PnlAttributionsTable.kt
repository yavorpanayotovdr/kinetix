package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object PnlAttributionsTable : Table("pnl_attributions") {
    val id = long("id").autoIncrement()
    val bookId = varchar("book_id", 64)
    val attributionDate = date("attribution_date")
    val totalPnl = decimal("total_pnl", 20, 8)
    val deltaPnl = decimal("delta_pnl", 20, 8)
    val gammaPnl = decimal("gamma_pnl", 20, 8)
    val vegaPnl = decimal("vega_pnl", 20, 8)
    val thetaPnl = decimal("theta_pnl", 20, 8)
    val rhoPnl = decimal("rho_pnl", 20, 8)
    val vannaPnl = decimal("vanna_pnl", 20, 8).default(java.math.BigDecimal.ZERO)
    val volgaPnl = decimal("volga_pnl", 20, 8).default(java.math.BigDecimal.ZERO)
    val charmPnl = decimal("charm_pnl", 20, 8).default(java.math.BigDecimal.ZERO)
    val crossGammaPnl = decimal("cross_gamma_pnl", 20, 8).default(java.math.BigDecimal.ZERO)
    val unexplainedPnl = decimal("unexplained_pnl", 20, 8)
    val positionAttributions = jsonb<List<PositionPnlAttributionJson>>("position_attributions", Json).nullable()
    val currency = varchar("currency", 3).default("USD")
    val dataQualityFlag = varchar("data_quality_flag", 32).default("PRICE_ONLY")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
