package com.kinetix.regulatory.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object StressTestResultsTable : Table("stress_test_results") {
    val id = varchar("id", 255)
    val scenarioId = varchar("scenario_id", 255)
    val portfolioId = varchar("portfolio_id", 255)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val basePv = decimal("base_pv", 28, 8).nullable()
    val stressedPv = decimal("stressed_pv", 28, 8).nullable()
    val pnlImpact = decimal("pnl_impact", 28, 8).nullable()
    val varImpact = double("var_impact").nullable()
    val positionImpacts = jsonb<JsonElement>("position_impacts", Json).nullable()
    val modelVersion = varchar("model_version", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}
