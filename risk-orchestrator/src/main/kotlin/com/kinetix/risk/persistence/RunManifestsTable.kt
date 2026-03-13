package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object RunManifestsTable : Table("run_manifests") {
    val manifestId = uuid("manifest_id")
    val jobId = uuid("job_id")
    val portfolioId = varchar("portfolio_id", 255)
    val valuationDate = date("valuation_date")
    val capturedAt = timestampWithTimeZone("captured_at")
    val modelVersion = varchar("model_version", 100)
    val calculationType = varchar("calculation_type", 50)
    val confidenceLevel = varchar("confidence_level", 10)
    val timeHorizonDays = integer("time_horizon_days")
    val numSimulations = integer("num_simulations")
    val monteCarloSeed = long("monte_carlo_seed")
    val positionCount = integer("position_count")
    val positionDigest = varchar("position_digest", 64)
    val marketDataDigest = varchar("market_data_digest", 64)
    val inputDigest = varchar("input_digest", 64)
    val status = varchar("status", 20)
    val varValue = double("var_value").nullable()
    val expectedShortfall = double("expected_shortfall").nullable()
    val outputDigest = varchar("output_digest", 64).nullable()

    override val primaryKey = PrimaryKey(manifestId)
}
