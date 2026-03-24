package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object MarketRegimeHistoryTable : Table("market_regime_history") {
    val id = uuid("id")
    val regime = varchar("regime", 32)
    val startedAt = timestampWithTimeZone("started_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    val durationMs = long("duration_ms").nullable()

    val realisedVol20d = decimal("realised_vol_20d", 12, 6)
    val crossAssetCorrelation = decimal("cross_asset_correlation", 12, 6)
    val creditSpreadBps = decimal("credit_spread_bps", 12, 4).nullable()
    val pnlVolatility = decimal("pnl_volatility", 12, 6).nullable()

    val calculationType = varchar("calculation_type", 32)
    val confidenceLevel = varchar("confidence_level", 16)
    val timeHorizonDays = integer("time_horizon_days")
    val correlationMethod = varchar("correlation_method", 32)
    val numSimulations = integer("num_simulations").nullable()

    val confidence = decimal("confidence", 6, 4)
    val degradedInputs = bool("degraded_inputs")
    val consecutiveObservations = integer("consecutive_observations")

    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id, startedAt)
}
