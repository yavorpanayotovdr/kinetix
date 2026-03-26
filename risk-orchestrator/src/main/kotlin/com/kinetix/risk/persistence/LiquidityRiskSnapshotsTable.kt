package com.kinetix.risk.persistence

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import java.util.UUID

object LiquidityRiskSnapshotsTable : Table("liquidity_risk_snapshots") {
    val snapshotId = uuid("snapshot_id").clientDefault { UUID.randomUUID() }
    val bookId = varchar("book_id", 255)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val portfolioLvar = decimal("portfolio_lvar", precision = 24, scale = 6)
    val dataCompleteness = decimal("data_completeness", precision = 5, scale = 4)
    val portfolioConcentrationStatus = varchar("portfolio_concentration_status", 20)
    val positionRisksJson = jsonb<JsonElement>("position_risks_json", Json)
    val var1day = decimal("var_1day", precision = 24, scale = 6).default(java.math.BigDecimal.ZERO)
    val lvarRatio = decimal("lvar_ratio", precision = 10, scale = 6).default(java.math.BigDecimal.ZERO)
    val weightedAvgHorizon = decimal("weighted_avg_horizon", precision = 10, scale = 4).default(java.math.BigDecimal.ZERO)
    val maxHorizon = decimal("max_horizon", precision = 10, scale = 4).default(java.math.BigDecimal.ZERO)
    val concentrationCount = integer("concentration_count").default(0)
    val advDataAsOf = timestampWithTimeZone("adv_data_as_of").nullable()

    override val primaryKey = PrimaryKey(snapshotId)
}
